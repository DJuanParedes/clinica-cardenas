package pe.clinicacardenas.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.AgendaService;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentRepository;
import pe.clinicacardenas.agenda.AppointmentStatus;
import pe.clinicacardenas.auth.PasswordPolicy;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.patient.PatientValidator;
import pe.clinicacardenas.security.JwtService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HU-22: aplicación del paciente. El paciente activa su cuenta con su documento, su fecha de nacimiento y un
 * código que recibe por WhatsApp (o correo), y luego entra con documento y contraseña para ver sus citas.
 * Solo puede ver y modificar su propia información: el identificador sale del token, nunca de la URL.
 */
@Service
public class PortalService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final PatientRepository patients;
    private final PatientAccountRepository accounts;
    private final PortalCodeRepository codes;
    private final AppointmentRepository appointments;
    private final AgendaService agenda;
    private final CashService cash;
    private final NotificationService notifications;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final boolean demoMode;
    private final String clinicName;
    private final String clinicAddress;
    private final String clinicPhone;
    private final SecureRandom random = new SecureRandom();

    public PortalService(PatientRepository patients, PatientAccountRepository accounts, PortalCodeRepository codes,
                         AppointmentRepository appointments, AgendaService agenda, CashService cash,
                         NotificationService notifications, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                         @Value("${app.demo-mode}") boolean demoMode,
                         @Value("${app.company.trade-name}") String clinicName,
                         @Value("${app.company.address}") String clinicAddress,
                         @Value("${app.notifications.clinic-phone:987 654 000}") String clinicPhone) {
        this.patients = patients;
        this.accounts = accounts;
        this.codes = codes;
        this.appointments = appointments;
        this.agenda = agenda;
        this.cash = cash;
        this.notifications = notifications;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.demoMode = demoMode;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicPhone = clinicPhone;
    }

    public record CodeRequest(String docType, String docNumber, LocalDate birthDate) {}
    public record ActivateRequest(String docType, String docNumber, String code, String password) {}
    public record LoginRequest(String docType, String docNumber, String password) {}
    public record ChannelRequest(String channel) {}

    private Optional<Patient> find(String docType, String docNumber) {
        String type = docType == null || docType.isBlank() ? "DNI" : docType.trim().toUpperCase();
        String doc = PatientValidator.normalizeDoc(docNumber);
        if (doc.isEmpty()) return Optional.empty();
        return patients.findByDocTypeAndDocNumber(type, doc).filter(p -> p.active);
    }

    private static String mask(String phone, String email, String channel) {
        if ("EMAIL".equals(channel) && email != null) {
            int at = email.indexOf('@');
            return email.charAt(0) + "•••" + (at > 0 ? email.substring(at) : "");
        }
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() < 3 ? "tu celular" : "•••• ••" + digits.substring(digits.length() - 3);
    }

    // ------------------------------------------------------------------ activación con código

    @Transactional
    public Map<String, Object> requestCode(CodeRequest r) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Si los datos coinciden con un paciente de la clínica, enviamos un código de 6 dígitos a su WhatsApp registrado.");
        if (r.birthDate() == null) throw ApiException.field("birthDate", "Ingresa tu fecha de nacimiento.");
        Optional<Patient> found = find(r.docType(), r.docNumber()).filter(p -> r.birthDate().equals(p.birthDate));
        if (found.isEmpty()) return res; // misma respuesta: no se revela si el documento existe
        Patient p = found.get();
        LocalDateTime now = Dates.now();
        List<PortalCode> open = codes.findByPatientIdAndUsedAtIsNullOrderByCreatedAtDesc(p.id);
        long recent = open.stream().filter(c -> c.createdAt.isAfter(now.minusMinutes(15))).count();
        if (recent >= 3) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_CODIGOS",
                    "Ya pediste varios códigos. Espera 15 minutos o comunícate con la clínica al " + clinicPhone + ".");
        }
        for (PortalCode c : open) c.usedAt = now; // invalida los anteriores
        String code = String.format("%06d", random.nextInt(1_000_000));
        PortalCode pc = new PortalCode();
        pc.patientId = p.id;
        pc.codeHash = encoder.encode(code);
        pc.expiresAt = now.plusMinutes(15);
        pc.createdAt = now;
        codes.save(pc);
        notifications.accessCode(p, code);
        audit.record("PACIENTE", p.id, "APP_CODIGO", "Código de acceso a la app enviado");
        res.put("sentTo", mask(p.phone, p.email, p.phone == null ? "EMAIL" : "WHATSAPP"));
        if (demoMode) res.put("demoCode", code);
        return res;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> activate(ActivateRequest r) {
        Patient p = find(r.docType(), r.docNumber())
                .orElseThrow(() -> ApiException.badRequest("CODIGO_INVALIDO", "El código no es válido o ya venció. Pide uno nuevo."));
        LocalDateTime now = Dates.now();
        PortalCode pc = codes.findByPatientIdAndUsedAtIsNullOrderByCreatedAtDesc(p.id).stream().findFirst()
                .filter(c -> c.expiresAt.isAfter(now) && c.attempts < MAX_ATTEMPTS)
                .orElseThrow(() -> ApiException.badRequest("CODIGO_INVALIDO", "El código no es válido o ya venció. Pide uno nuevo."));
        if (r.code() == null || !encoder.matches(r.code().trim(), pc.codeHash)) {
            pc.attempts++;
            codes.save(pc);
            int left = MAX_ATTEMPTS - pc.attempts;
            throw ApiException.field("code", left > 0 ? "Código incorrecto. Te quedan " + left + " intentos." : "Código bloqueado. Pide uno nuevo.");
        }
        PasswordPolicy.check("password", r.password());
        PatientAccount acc = accounts.findById(p.id).orElseGet(() -> {
            PatientAccount a = new PatientAccount();
            a.patientId = p.id;
            a.createdAt = now;
            return a;
        });
        acc.passwordHash = encoder.encode(r.password());
        acc.active = true;
        acc.failedAttempts = 0;
        acc.lockedUntil = null;
        if (acc.activatedAt == null) acc.activatedAt = now;
        accounts.save(acc);
        pc.usedAt = now;
        codes.save(pc);
        audit.record("PACIENTE", p.id, "APP_ACTIVAR", "Cuenta de la app activada o contraseña restablecida");
        return session(p, acc);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> login(LoginRequest r) {
        ApiException invalid = new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Documento o contraseña incorrectos.");
        Patient p = find(r.docType(), r.docNumber()).orElseThrow(() -> invalid);
        PatientAccount acc = accounts.findById(p.id).orElse(null);
        if (acc == null || acc.passwordHash == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CUENTA_NO_ACTIVADA", "Aún no activaste tu cuenta. Toca \"Activar mi cuenta\" para crear tu contraseña.");
        }
        if (!acc.active) throw new ApiException(HttpStatus.FORBIDDEN, "CUENTA_INACTIVA", "Tu acceso está desactivado. Comunícate con la clínica.");
        LocalDateTime now = Dates.now();
        if (acc.lockedUntil != null && acc.lockedUntil.isAfter(now)) {
            long min = Math.max(1, (Duration.between(now, acc.lockedUntil).getSeconds() + 59) / 60);
            throw new ApiException(HttpStatus.LOCKED, "CUENTA_BLOQUEADA", "Demasiados intentos. Intenta en " + min + " min o restablece tu contraseña.");
        }
        if (r.password() == null || !encoder.matches(r.password(), acc.passwordHash)) {
            acc.failedAttempts++;
            if (acc.failedAttempts >= MAX_ATTEMPTS) {
                acc.failedAttempts = 0;
                acc.lockedUntil = now.plusMinutes(LOCK_MINUTES);
                accounts.save(acc);
                throw new ApiException(HttpStatus.LOCKED, "CUENTA_BLOQUEADA", "Superaste " + MAX_ATTEMPTS + " intentos. Tu acceso quedó bloqueado " + LOCK_MINUTES + " minutos.");
            }
            accounts.save(acc);
            throw invalid;
        }
        acc.failedAttempts = 0;
        acc.lockedUntil = null;
        acc.lastLoginAt = now;
        accounts.save(acc);
        return session(p, acc);
    }

    private Map<String, Object> session(Patient p, PatientAccount acc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwt.generate(PatientAccount.SUBJECT_PREFIX + p.id, "PACIENTE"));
        m.put("expiresInMinutes", jwt.getExpirationMinutes());
        m.put("patient", profile(p));
        return m;
    }

    // ------------------------------------------------------------------ datos del paciente autenticado

    public static Long patientIdFrom(String subject) {
        if (subject == null || !subject.startsWith(PatientAccount.SUBJECT_PREFIX)) throw new ApiException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Acceso solo para pacientes.");
        return Long.parseLong(subject.substring(PatientAccount.SUBJECT_PREFIX.length()));
    }

    private Patient me(String subject) {
        return patients.findById(patientIdFrom(subject)).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    public Map<String, Object> profile(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("firstName", p.firstName);
        m.put("fullName", p.getFullName());
        m.put("docType", p.docType);
        m.put("docNumber", p.docNumber.length() > 4 ? "••••" + p.docNumber.substring(p.docNumber.length() - 4) : p.docNumber);
        m.put("phone", p.phone);
        m.put("email", p.email);
        m.put("notifyChannel", p.notifyChannel);
        m.put("clinic", Map.of("name", clinicName, "address", clinicAddress, "phone", clinicPhone));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profileOf(String subject) {
        return profile(me(subject));
    }

    private Map<String, Object> view(Appointment a, LocalDateTime now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("startAt", a.startAt);
        m.put("endAt", a.endAt);
        m.put("durationMin", a.durationMin);
        m.put("reason", a.reason);
        m.put("status", a.status);
        m.put("dentist", Map.of("fullName", a.dentist.getFullName(), "specialty", a.dentist.specialty, "color", a.dentist.color));
        m.put("canConfirm", a.status == AppointmentStatus.PROGRAMADA && a.startAt.isAfter(now));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> appointments(String subject) {
        Patient p = me(subject);
        LocalDateTime now = Dates.now();
        List<Map<String, Object>> upcoming = new ArrayList<>(), past = new ArrayList<>();
        List<Appointment> all = appointments.findByPatientIdOrderByStartAtDesc(p.id);
        for (Appointment a : all) {
            boolean open = !a.status.isFinal() && a.endAt.isAfter(now);
            if (open) upcoming.add(0, view(a, now));
            else if (past.size() < 30) past.add(view(a, now));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("next", upcoming.isEmpty() ? null : upcoming.get(0));
        m.put("upcoming", upcoming);
        m.put("past", past);
        return m;
    }

    @Transactional
    public Map<String, Object> confirm(String subject, Long appointmentId) {
        Patient p = me(subject);
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("La cita"));
        if (!a.patient.id.equals(p.id)) throw ApiException.notFound("La cita"); // no se revela que existe
        if (a.status != AppointmentStatus.PROGRAMADA || !a.startAt.isAfter(Dates.now())) {
            throw ApiException.conflict("NO_CONFIRMABLE", "Esta cita ya no se puede confirmar.");
        }
        agenda.changeStatus(a.id, new AgendaService.StatusRequest(AppointmentStatus.CONFIRMADA, "Confirmada por el paciente desde la app"));
        return appointments(subject);
    }

    @Transactional
    public Map<String, Object> updateChannel(String subject, String channel) {
        Patient p = me(subject);
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!NotificationService.CHANNEL_PREFS.contains(ch)) throw ApiException.field("channel", "Elige cómo quieres recibir los avisos.");
        if (("EMAIL".equals(ch) || "AMBOS".equals(ch)) && (p.email == null || p.email.isBlank())) {
            throw ApiException.field("channel", "No tenemos tu correo. Pídelo a recepción para registrarlo.");
        }
        p.notifyChannel = ch;
        p.updatedAt = Dates.now();
        patients.save(p);
        audit.record("PACIENTE", p.id, "CANAL_AVISOS", "Desde la app: avisos por " + ch.toLowerCase());
        return profile(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> account(String subject) {
        Map<String, Object> b = cash.balance(me(subject).id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", b.get("balance"));
        m.put("inFavor", b.get("inFavor"));
        m.put("charges", b.get("charges"));
        m.put("credits", b.get("credits"));
        m.put("movements", b.get("movements"));
        return m;
    }
}
