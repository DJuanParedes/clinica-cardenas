package pe.clinicacardenas.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.clinical.AnamnesisService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.*;
import pe.clinicacardenas.portal.PortalExtrasService;
import pe.clinicacardenas.portal.Survey;
import pe.clinicacardenas.portal.SurveyRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.6: lo que el paciente hace sin iniciar sesión, desde enlaces que la clínica comparte (Instagram, WhatsApp, Google):
 * reservar una cita, llenar su ficha antes de llegar (prerregistro) y calificar la atención (encuesta).
 *
 * Protecciones: consentimiento Ley 29733 obligatorio; un paciente ya registrado solo reserva si su fecha de
 * nacimiento coincide (no se revela nada de la ficha); límites por IP y por celular contra reservas masivas; la
 * cita queda "programada" y el paciente la confirma con el botón del recordatorio.
 */
@Service
public class PublicPatientService {
    private static final String CLINIC = "clinic";
    private static final String FIRSTNAME = "firstName";
    private static final String MESSAGE = "message";
    private static final String RESERVA_PUBLICA_ACTIVA = "reserva_publica_activa";
    private static final String STARTAT = "startAt";
    private static final String TEMPLATEID = "templateId";


    static final int MIN_HOURS = 2, MAX_DAYS = 60, MAX_OPEN = 2, IP_PER_HOUR = 8, PHONE_PER_DAY = 2;

    private final JdbcTemplate jdbc;
    private final AgendaService agenda;
    private final AppointmentRepository appointments;
    private final PatientService patients;
    private final PatientRepository patientRepo;
    private final AnamnesisService anamnesis;
    private final SurveyRepository surveys;
    private final SurveyLinks links;
    private final AuditService audit;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private pe.clinicacardenas.crm.MarketingService marketing;
    private final String clinicName, clinicAddress, clinicPhone;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public PublicPatientService(JdbcTemplate jdbc, AgendaService agenda, AppointmentRepository appointments, PatientService patients,
                                PatientRepository patientRepo, AnamnesisService anamnesis, SurveyRepository surveys, SurveyLinks links,
                                AuditService audit, @Value("${app.company.trade-name}") String clinicName,
                                @Value("${app.company.address}") String clinicAddress,
                                @Value("${app.notifications.clinic-phone:987 654 000}") String clinicPhone) {
        this.jdbc = jdbc;
        this.agenda = agenda;
        this.appointments = appointments;
        this.patients = patients;
        this.patientRepo = patientRepo;
        this.anamnesis = anamnesis;
        this.surveys = surveys;
        this.links = links;
        this.audit = audit;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicPhone = clinicPhone;
    }

    public record PersonData(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate, String sex,
                             String phone, String email, String address, String ubigeo, String guardianName, String guardianDocNumber,
                             String guardianRelationship, String guardianPhone, String emergencyContactName, String emergencyContactPhone,
                             String source, Boolean dataConsent, Boolean marketingConsent) {}

    public record BookingRequest(PersonData person, Long dentistId, LocalDateTime startAt, String reasonCode, String notes) {}

    public record PreregisterRequest(PersonData person, Map<String, Map<String, Object>> answers, String allergies, String medication) {}

    public record SurveyRequest(Integer score, String comment) {}

    // ------------------------------------------------------------------ reserva

    public Map<String, Object> clinic() {
        return Map.of("name", clinicName, "address", clinicAddress, "phone", clinicPhone);
    }

    public Map<String, Object> bookingOptions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", setting(RESERVA_PUBLICA_ACTIVA));
        m.put(CLINIC, clinic());
        if (!setting(RESERVA_PUBLICA_ACTIVA)) return m;
        List<Map<String, Object>> ds = new ArrayList<>();
        for (Dentist d : agenda.activeDentists()) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.id);
            dm.put("fullName", d.getFullName());
            dm.put("specialty", d.specialty);
            dm.put("photoUrl", d.getPhotoUrl());
            ds.add(dm);
        }
        m.put("dentists", ds);
        m.put("reasons", PortalExtrasService.REASONS);
        m.put("rules", Map.of("minHours", MIN_HOURS, "maxDays", MAX_DAYS));
        return m;
    }

    public Map<String, Object> slots(Long dentistId, LocalDate date, String reasonCode) {
        enabled(RESERVA_PUBLICA_ACTIVA);
        int duration = duration(reasonCode);
        List<String> free = new ArrayList<>();
        if (!date.isBefore(Dates.today()) && !date.isAfter(Dates.today().plusDays(MAX_DAYS))) {
            Object list = agenda.slots(dentistId, date, duration, null).get("slots");
            LocalDateTime min = Dates.now().plusHours(MIN_HOURS);
            if (list instanceof List<?> l) for (Object o : l) {
                if (o instanceof SlotCalculator.Slot s && s.available() && !s.start().isBefore(min)) free.add(s.start().toString());
            }
        }
        return Map.of("date", date, "durationMin", duration, "slots", free);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> book(BookingRequest r, String ip) {
        enabled(RESERVA_PUBLICA_ACTIVA);
        limit("ip:" + ip, IP_PER_HOUR, 3600_000L, "Hiciste muchas reservas seguidas. Inténtalo en una hora o llámanos al " + clinicPhone + ".");
        String label = label(r.reasonCode());
        if (label == null) throw ApiException.field("reasonCode", "Elige el motivo de tu cita.");
        if (r.dentistId() == null) throw ApiException.field("dentistId", "Elige el odontólogo.");
        if (r.startAt() == null) throw ApiException.field(STARTAT, "Elige un horario.");
        if (r.startAt().isBefore(Dates.now().plusHours(MIN_HOURS))) throw ApiException.field(STARTAT, "Reserva con al menos " + MIN_HOURS + " horas de anticipación.");
        if (r.startAt().toLocalDate().isAfter(Dates.today().plusDays(MAX_DAYS))) throw ApiException.field(STARTAT, "Puedes reservar hasta " + MAX_DAYS + " días antes.");
        Patient p = findOrCreate(r.person(), ip);
        marketingOptIn(p, r.person(), ip);
        limit("tel:" + p.id, PHONE_PER_DAY, 86_400_000L, "Ya hiciste reservas hoy. Si necesitas otra cita, llámanos al " + clinicPhone + ".");
        long open = appointments.findByPatientIdOrderByStartAtDesc(p.id).stream().filter(a -> !a.status.isFinal() && a.endAt.isAfter(Dates.now())).count();
        if (open >= MAX_OPEN) {
            throw ApiException.conflict("LIMITE_CITAS", "Ya tienes " + open + " citas pendientes con nosotros. Para cambiarlas o pedir otra, escríbenos o llámanos al " + clinicPhone + ".");
        }
        String notes = "Reservada desde el enlace web" + (r.notes() == null || r.notes().isBlank() ? "" : ": " + r.notes().trim());
        String phone = r.person() == null ? null : PatientValidator.normalizePhone(r.person().phone());
        if (phone != null && p.phone != null && !phone.equals(p.phone)) notes += " · Celular indicado en la web: " + phone;
        String fn = notes.length() > 480 ? notes.substring(0, 480) : notes;
        Appointment a = NotificationService.withCreatedTemplate("RESERVA_WEB", () -> agenda.create(
                new AgendaService.AppointmentRequest(p.id, r.dentistId(), r.startAt(), duration(r.reasonCode()), label, fn, null)));
        a.bookedVia = "WEB";
        appointments.save(a);
        audit.record("CITA", a.id, "RESERVA_WEB", "Reservada por " + p.getFullName() + " desde el enlace público");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put(STARTAT, a.startAt);
        m.put("dentist", a.dentist.getFullName());
        m.put(FIRSTNAME, p.firstName.split(" ")[0]);
        m.put(MESSAGE, "¡Listo! Tu cita quedó reservada. Te enviamos la confirmación por " + (p.phone != null ? "WhatsApp" : "correo") + ".");
        return m;
    }

    /** Paciente nuevo: se registra. Ya registrado: debe coincidir la fecha de nacimiento (no se cambia su ficha). */
    private Patient findOrCreate(PersonData d, String ip) {
        if (d == null) throw ApiException.badRequest("SIN_DATOS", "Completa tus datos.");
        if (!Boolean.TRUE.equals(d.dataConsent())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_CONSENTIMIENTO", "Para reservar debes aceptar la política de privacidad.",
                    Map.of("dataConsent", "Lee y acepta la política de tratamiento de datos personales (Ley N.° 29733)."));
        }
        String type = d.docType() == null ? "DNI" : d.docType().trim().toUpperCase();
        String doc = PatientValidator.normalizeDoc(d.docNumber());
        Optional<Patient> existing = patientRepo.findByDocTypeAndDocNumber(type, doc);
        if (existing.isPresent()) {
            Patient p = existing.get();
            if (d.birthDate() == null || !d.birthDate().equals(p.birthDate)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DATOS_NO_COINCIDEN",
                        "Tu documento ya está registrado, pero la fecha de nacimiento no coincide con nuestra ficha. Revísala o llámanos al " + clinicPhone + ".",
                        Map.of("birthDate", "No coincide con la ficha registrada."));
            }
            if (!p.active) throw ApiException.conflict("PACIENTE_INACTIVO", "Para reservar comunícate con la clínica al " + clinicPhone + ".");
            return p;
        }
        Patient p = patients.createFromWeb(new PatientValidator.Input(type, doc, d.firstName(), d.lastName(), d.birthDate(), d.sex(),
                d.phone(), d.email(), d.address(), null, d.emergencyContactName(), d.emergencyContactPhone(), null, true,
                d.ubigeo(), null, d.guardianName(), "DNI", d.guardianDocNumber(), d.guardianRelationship(), d.guardianPhone()), ip);
        p.source = d.source() != null && PatientProfileService.SOURCES.stream().anyMatch(x -> x.get("code").equals(d.source())) ? d.source() : "WEB";
        patientRepo.save(p);
        return p;
    }

    // ------------------------------------------------------------------ prerregistro

    public Map<String, Object> preregisterForm() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", setting("prerregistro_activo"));
        m.put(CLINIC, clinic());
        m.put("sources", PatientProfileService.SOURCES);
        // Preguntas de salud de la anamnesis general que la clínica marcó como alerta (Sí / No).
        List<Map<String, Object>> t = anamnesis.templates(false).stream().filter(x -> "ODONTOLOGIA".equals(x.get("code"))).toList();
        if (!t.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<AnamnesisService.Question> qs = (List<AnamnesisService.Question>) t.get(0).get("questions");
            m.put(TEMPLATEID, t.get(0).get("id"));
            m.put("questions", qs.stream().filter(q -> "YES_NO".equals(q.type()) && Boolean.TRUE.equals(q.alert())).toList());
        }
        return m;
    }

    @Transactional
    public Map<String, Object> preregister(PreregisterRequest r, String ip) {
        enabled("prerregistro_activo");
        limit("pre:" + ip, IP_PER_HOUR, 3600_000L, "Hiciste muchos registros seguidos. Inténtalo en una hora.");
        PersonData d = r.person();
        if (d == null) throw ApiException.badRequest("SIN_DATOS", "Completa tus datos.");
        String type = d.docType() == null ? "DNI" : d.docType().trim().toUpperCase();
        if (patientRepo.findByDocTypeAndDocNumber(type, PatientValidator.normalizeDoc(d.docNumber())).isPresent()) {
            throw ApiException.conflict("YA_REGISTRADO", "Ya estás registrado en la clínica. Si cambiaron tus datos, te los actualizamos en recepción. Puedes reservar tu cita desde " + links.bookingUrl() + ".");
        }
        Patient p = findOrCreate(d, ip);
        marketingOptIn(p, d, ip);
        Map<String, Object> form = preregisterForm();
        if (form.get(TEMPLATEID) != null && r.answers() != null && !r.answers().isEmpty()) {
            try {
                anamnesis.saveRecord(p.id, null, new AnamnesisService.RecordRequest(((Number) form.get(TEMPLATEID)).longValue(), r.answers()));
                jdbc.update("update anamnesis_records set created_by = 'paciente (web)' where patient_id = ?", p.id);
            } catch (ApiException ignored) { /* sin respuestas válidas: no se guarda la anamnesis */ }
        }
        String allergies = clean(r.allergies(), 500), medication = clean(r.medication(), 500);
        if (allergies != null || medication != null) {
            jdbc.update("insert into clinical_histories (patient_id, allergies, current_medication, created_at, updated_at, updated_by) values (?,?,?,?,?,?)",
                    p.id, allergies, medication, Dates.now(), Dates.now(), "paciente (web)");
        }
        audit.record("PACIENTE", p.id, "PRERREGISTRO", "Ficha llenada por el paciente desde el enlace web");
        return Map.of("id", p.id, FIRSTNAME, p.firstName.split(" ")[0], "hcNumber", p.hcNumber == null ? "" : p.hcNumber,
                MESSAGE, "¡Gracias! Tu ficha quedó registrada. Ya puedes reservar tu cita.", "bookingUrl", links.bookingUrl());
    }

    // ------------------------------------------------------------------ encuesta

    public Map<String, Object> survey(String token) {
        Appointment a = byToken(token);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(CLINIC, clinicName);
        m.put(FIRSTNAME, a.patient.firstName.split(" ")[0]);
        m.put("dentist", a.dentist.getFullName());
        m.put("date", a.startAt.toLocalDate());
        m.put("reason", a.reason);
        m.put("rated", surveys.existsByAppointmentId(a.id));
        return m;
    }

    @Transactional
    public Map<String, Object> rate(String token, SurveyRequest r) {
        Appointment a = byToken(token);
        if (surveys.existsByAppointmentId(a.id)) throw ApiException.conflict("YA_CALIFICADA", "Ya calificaste esta atención. ¡Gracias!");
        if (r.score() == null || r.score() < 1 || r.score() > 5) throw ApiException.field("score", "Elige de 1 a 5 estrellas.");
        Survey s = new Survey();
        s.appointmentId = a.id;
        s.patientId = a.patient.id;
        s.dentistId = a.dentist.id;
        s.score = r.score();
        s.comment = clean(r.comment(), 500);
        s.createdAt = Dates.now();
        surveys.save(s);
        audit.record("CITA", a.id, "ENCUESTA", r.score() + " estrellas desde el enlace del mensaje");
        return Map.of(MESSAGE, r.score() >= 4 ? "¡Gracias! Nos alegra que te haya gustado tu atención." : "Gracias por contarnos. Revisaremos tu comentario para mejorar.");
    }

    private Appointment byToken(String token) {
        Long id = links.parse(token);
        if (id == null) throw ApiException.notFound("El enlace");
        Appointment a = appointments.findById(id).orElseThrow(() -> ApiException.notFound("El enlace"));
        if (a.status != AppointmentStatus.ATENDIDA) throw ApiException.conflict("SIN_ATENCION", "Esta cita aún no figura como atendida.");
        if (a.startAt.isBefore(Dates.now().minusDays(30))) throw ApiException.conflict("ENLACE_VENCIDO", "Este enlace venció (30 días).");
        return a;
    }

    // ------------------------------------------------------------------ utilidades

    private boolean setting(String k) {
        return jdbc.queryForList("select v from app_settings where k = ?", String.class, k).stream().findFirst().map("true"::equals).orElse(false);
    }

    private void enabled(String k) {
        if (!setting(k)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Por ahora la clínica no recibe reservas por la web. Llámanos o escríbenos al " + clinicPhone + ".");
    }

    private void limit(String key, int max, long windowMs, String msg) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, x -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
        if (q.size() >= max) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", msg);
        q.addLast(now);
    }

    private static int duration(String code) {
        for (Map<String, Object> r : PortalExtrasService.REASONS) if (r.get("code").equals(code)) return (Integer) r.get("durationMin");
        return 30;
    }

    private static String label(String code) {
        for (Map<String, Object> r : PortalExtrasService.REASONS) if (r.get("code").equals(code)) return (String) r.get("label");
        return null;
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** v2.7: casilla opcional "acepto recibir promociones" (aparte del consentimiento de datos). */
    private void marketingOptIn(Patient p, PersonData d, String ip) {
        if (Boolean.TRUE.equals(d.marketingConsent()) && !Boolean.TRUE.equals(p.marketingConsent))
            marketing.applyConsent(p, true, "WEB", "paciente (enlace web)", ip);
    }
}
