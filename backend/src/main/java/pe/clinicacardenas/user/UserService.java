package pe.clinicacardenas.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentRepository;
import pe.clinicacardenas.agenda.AppointmentStatus;
import pe.clinicacardenas.agenda.Dentist;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.agenda.DentistSchedule;
import pe.clinicacardenas.agenda.DentistScheduleRepository;
import pe.clinicacardenas.agenda.SlotCalculator;
import pe.clinicacardenas.auth.PasswordPolicy;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,40}$");
    private static final Pattern EMAIL = pe.clinicacardenas.common.Emails.PATTERN;
    private static final String[] COLORS = {"#00A3D4", "#F08A00", "#1E5FAE", "#7A4FC9", "#217A4A", "#C2410C"};

    private final UserRepository users;
    private final DentistRepository dentists;
    private final DentistScheduleRepository schedules;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppointmentRepository appointments;

    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public UserService(UserRepository users, DentistRepository dentists, DentistScheduleRepository schedules,
                       PasswordEncoder encoder, AuditService audit, AppointmentRepository appointments) {
        this.appointments = appointments;
        this.users = users;
        this.dentists = dentists;
        this.schedules = schedules;
        this.encoder = encoder;
        this.audit = audit;
    }

    public record UserRequest(String username, String email, String fullName, Role role, String password,
                              String specialty, String cop, String phone, String accessRole) {
        public UserRequest(String username, String email, String fullName, Role role, String password, String specialty, String cop) {
            this(username, email, fullName, role, password, specialty, cop, null, null);
        }
    }

    public record ProfileRequest(String fullName, String email, String phone) {}

    public record PasswordChange(String currentPassword, String newPassword) {}

    private pe.clinicacardenas.security.PermissionService permissions;

    @org.springframework.beans.factory.annotation.Autowired
    void setPermissions(pe.clinicacardenas.security.PermissionService permissions) {
        this.permissions = permissions;
    }

    private static final Pattern PHONE = Pattern.compile("^9\\d{8}$");

    public record ScheduleBlock(int weekday, String start, String end) {}

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users.findAllByOrderByFullNameAsc()) {
            out.add(view(u));
        }
        return out;
    }

    public Map<String, Object> view(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("username", u.username);
        m.put("email", u.email);
        m.put("fullName", u.fullName);
        m.put("role", u.role.name());
        m.put("roleLabel", u.role.label);
        m.put("phone", u.phone);
        m.put("accessRole", u.accessRole);
        m.put("accessRoleLabel", u.accessRole == null ? u.role.label : permissions.label(u.accessRole));
        m.put("twoFactorMethod", u.twoFactorMethod);
        m.put("active", u.active);
        m.put("locked", u.lockedUntil != null && u.lockedUntil.isAfter(Dates.now()));
        m.put("lastLoginAt", u.lastLoginAt);
        m.put("createdAt", u.createdAt);
        m.put("updatedAt", u.updatedAt);
        m.put("updatedBy", u.updatedBy);
        dentists.findByUserId(u.id).ifPresent(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.id);
            dm.put("specialty", d.specialty);
            dm.put("cop", d.cop);
            dm.put("color", d.color);
            dm.put("photoUrl", d.getPhotoUrl());
            m.put("dentist", dm);
        });
        return m;
    }

    // ------------------------------------------------------------------ escritura

    private void validate(UserRequest r, boolean creating, Long id) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (creating) {
            if (r.username() == null || !USERNAME.matcher(r.username().trim()).matches()) {
                errors.put("username", "Usa de 3 a 40 caracteres: letras, números, punto, guion o guion bajo.");
            } else if (users.existsByUsernameIgnoreCase(r.username().trim())) {
                errors.put("username", "Ese nombre de usuario ya está en uso.");
            }
            if (!PasswordPolicy.isValid(r.password())) {
                errors.put("password", "Mínimo 8 caracteres, con al menos una letra y un número.");
            }
        }
        if (r.fullName() == null || r.fullName().trim().length() < 3) {
            errors.put("fullName", "Ingresa nombres y apellidos.");
        }
        if (r.email() == null || !EMAIL.matcher(r.email().trim()).matches()) {
            errors.put("email", "Ingresa un correo válido.");
        } else {
            users.findByEmailIgnoreCase(r.email().trim()).ifPresent(other -> {
                if (!Objects.equals(other.id, id)) errors.put("email", "Ese correo ya pertenece a otra cuenta.");
            });
        }
        if (r.role() == null) {
            errors.put("role", "Selecciona un rol.");
        }
        if (r.role() == Role.ODONTOLOGO && (r.specialty() == null || r.specialty().isBlank())) {
            errors.put("specialty", "Indica la especialidad del odontólogo.");
        }
        if (r.phone() != null && !r.phone().isBlank() && !PHONE.matcher(r.phone().replaceAll("\\s", "")).matches()) {
            errors.put("phone", "Celular de 9 dígitos que empiece con 9.");
        }
        if (r.accessRole() != null && !r.accessRole().isBlank() && r.role() != null
                && !r.accessRole().equals(r.role().name()) && !permissions.isCustomFor(r.accessRole(), r.role())) {
            errors.put("accessRole", "Ese rol no corresponde al puesto elegido.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Revisa los campos marcados.", errors);
        }
    }

    @Transactional
    public Map<String, Object> create(UserRequest r) {
        validate(r, true, null);
        User u = new User();
        u.username = r.username().trim().toLowerCase();
        u.email = r.email().trim().toLowerCase();
        u.fullName = r.fullName().trim();
        u.role = r.role();
        u.phone = phone(r.phone());
        u.accessRole = access(r);
        u.passwordHash = encoder.encode(r.password());
        u.active = true;
        u.createdAt = Dates.now();
        u.createdBy = CurrentUser.usernameOrSystem();
        users.save(u);
        if (u.role == Role.ODONTOLOGO) {
            ensureDentist(u, r.specialty(), r.cop());
        }
        audit.record("USUARIO", u.id, "CREAR", "Usuario " + u.username + " con rol " + u.role.label);
        return view(u);
    }

    @Transactional
    public Map<String, Object> update(Long id, UserRequest r) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario"));
        validate(r, false, id);
        if (u.username.equals(CurrentUser.usernameOrSystem()) && u.role == Role.ADMIN && r.role() != Role.ADMIN) {
            throw ApiException.field("role", "No puedes quitarte el rol de administrador a ti mismo.");
        }
        List<String> changes = new ArrayList<>();
        if (!u.fullName.equals(r.fullName().trim())) changes.add("nombre");
        if (!u.email.equalsIgnoreCase(r.email().trim())) changes.add("correo");
        if (u.role != r.role()) changes.add("rol " + u.role.label + " → " + r.role().label);
        u.fullName = r.fullName().trim();
        u.email = r.email().trim().toLowerCase();
        u.role = r.role();
        if (r.phone() != null) u.phone = phone(r.phone());
        String acc = access(r);
        if (!Objects.equals(acc, u.accessRole)) changes.add("rol de acceso → " + (acc == null ? u.role.label : permissions.label(acc)));
        u.accessRole = acc;
        u.updatedAt = Dates.now();
        u.updatedBy = CurrentUser.usernameOrSystem();
        users.save(u);
        Optional<Dentist> dentist = dentists.findByUserId(u.id);
        if (u.role != Role.ODONTOLOGO) dentist.filter(d -> d.active).ifPresent(d -> ensureNoPendingAppointments(d, "cambiarle el rol"));
        if (u.role == Role.ODONTOLOGO) {
            ensureDentist(u, r.specialty(), r.cop());
        } else {
            dentist.ifPresent(d -> { d.active = false; dentists.save(d); });
        }
        audit.record("USUARIO", u.id, "EDITAR", changes.isEmpty() ? "Datos revisados sin cambios" : String.join(", ", changes));
        return view(u);
    }

    private static String phone(String p) {
        return p == null || p.isBlank() ? null : p.replaceAll("\\s", "");
    }

    private static String access(UserRequest r) {
        return r.accessRole() == null || r.accessRole().isBlank() || r.accessRole().equals(r.role().name()) ? null : r.accessRole();
    }

    // ------------------------------------------------------------------ mi perfil (cada usuario)

    @Transactional
    public Map<String, Object> updateOwnProfile(ProfileRequest r) {
        User u = users.findByUsernameIgnoreCase(CurrentUser.usernameOrSystem()).orElseThrow(() -> ApiException.notFound("El usuario"));
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.fullName() == null || r.fullName().trim().length() < 3) errors.put("fullName", "Ingresa nombres y apellidos.");
        if (r.email() == null || !EMAIL.matcher(r.email().trim()).matches()) errors.put("email", "Ingresa un correo válido.");
        else users.findByEmailIgnoreCase(r.email().trim()).ifPresent(o -> { if (!o.id.equals(u.id)) errors.put("email", "Ese correo ya pertenece a otra cuenta."); });
        if (r.phone() != null && !r.phone().isBlank() && !PHONE.matcher(r.phone().replaceAll("\\s", "")).matches()) errors.put("phone", "Celular de 9 dígitos que empiece con 9.");
        if (!errors.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        u.fullName = r.fullName().trim();
        u.email = r.email().trim().toLowerCase();
        u.phone = phone(r.phone());
        u.updatedAt = Dates.now();
        u.updatedBy = u.username;
        users.save(u);
        audit.record("USUARIO", u.id, "PERFIL", "Actualizó sus datos personales");
        return view(u);
    }

    /** Cambio de contraseña por el propio usuario: exige la actual. */
    @Transactional
    public void changeOwnPassword(PasswordChange r) {
        User u = users.findByUsernameIgnoreCase(CurrentUser.usernameOrSystem()).orElseThrow(() -> ApiException.notFound("El usuario"));
        if (r.currentPassword() == null || !encoder.matches(r.currentPassword(), u.passwordHash)) {
            throw ApiException.field("currentPassword", "La contraseña actual no es correcta.");
        }
        PasswordPolicy.check("newPassword", r.newPassword());
        if (encoder.matches(r.newPassword(), u.passwordHash)) throw ApiException.field("newPassword", "La nueva contraseña debe ser distinta a la actual.");
        u.passwordHash = encoder.encode(r.newPassword());
        u.updatedAt = Dates.now();
        u.updatedBy = u.username;
        users.save(u);
        audit.record("USUARIO", u.id, "CAMBIO_CLAVE", "Cambió su contraseña");
    }

    private void ensureDentist(User u, String specialty, String cop) {
        Dentist d = dentists.findByUserId(u.id).orElse(null);
        boolean isNew = d == null;
        if (isNew) {
            d = new Dentist();
            d.user = u;
            d.color = COLORS[(int) (dentists.count() % COLORS.length)];
        }
        d.specialty = specialty == null || specialty.isBlank() ? "Odontología general" : specialty.trim();
        d.cop = cop == null || cop.isBlank() ? null : cop.trim();
        d.active = true;
        dentists.save(d);
        if (isNew) {
            defaultSchedule(d.id);
        }
    }

    /** Horario por defecto: L-V 09:00-13:00 y 15:00-19:00, sábado 09:00-13:00. */
    public void defaultSchedule(Long dentistId) {
        for (int day = 1; day <= 5; day++) {
            schedules.save(new DentistSchedule(dentistId, day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
            schedules.save(new DentistSchedule(dentistId, day, LocalTime.of(15, 0), LocalTime.of(19, 0)));
        }
        schedules.save(new DentistSchedule(dentistId, 6, LocalTime.of(9, 0), LocalTime.of(13, 0)));
    }

    @Transactional
    public Map<String, Object> setActive(Long id, boolean active) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario"));
        if (!active && u.username.equals(CurrentUser.usernameOrSystem())) {
            throw ApiException.badRequest("OPERACION_NO_PERMITIDA", "No puedes desactivar tu propia cuenta.");
        }
        if (!active) dentists.findByUserId(u.id).filter(d -> d.active).ifPresent(d -> ensureNoPendingAppointments(d, "desactivarlo"));
        u.active = active;
        u.updatedAt = Dates.now();
        u.updatedBy = CurrentUser.usernameOrSystem();
        users.save(u);
        dentists.findByUserId(u.id).ifPresent(d -> { d.active = active; dentists.save(d); });
        audit.record("USUARIO", u.id, active ? "ACTIVAR" : "DESACTIVAR", (active ? "Cuenta activada" : "Cuenta desactivada"));
        return view(u);
    }

    @Transactional
    public void adminResetPassword(Long id, String newPassword) {
        PasswordPolicy.check("newPassword", newPassword);
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario"));
        u.passwordHash = encoder.encode(newPassword);
        u.failedAttempts = 0;
        u.lockedUntil = null;
        u.updatedAt = Dates.now();
        u.updatedBy = CurrentUser.usernameOrSystem();
        users.save(u);
        audit.record("USUARIO", u.id, "CONTRASENA_ASIGNADA", "Contraseña temporal asignada por el administrador");
    }

    @Transactional
    public Map<String, Object> unlock(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario"));
        u.failedAttempts = 0;
        u.lockedUntil = null;
        users.save(u);
        audit.record("USUARIO", u.id, "DESBLOQUEAR", "Cuenta desbloqueada");
        return view(u);
    }

    /** Un odontólogo con citas por atender no se desactiva: esas citas quedarían sin profesional. */
    private void ensureNoPendingAppointments(Dentist d, String action) {
        List<Appointment> pending = appointments.findPendingForDentist(d.id, Dates.now(), AppointmentStatus.PENDING);
        if (!pending.isEmpty()) {
            throw ApiException.conflict("CITAS_PENDIENTES", d.getFullName() + " tiene " + pending.size()
                    + " cita(s) pendiente(s) (la próxima: " + pending.get(0).startAt.format(DMY_HM)
                    + "). Reprográmalas con otro odontólogo o cancélalas antes de " + action + ".");
        }
    }

    // ------------------------------------------------------------------ horarios

    @Transactional(readOnly = true)
    public List<DentistSchedule> schedules(Long dentistId) {
        dentists.findById(dentistId).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        return schedules.findByDentistIdOrderByWeekdayAscStartTimeAsc(dentistId);
    }

    @Transactional
    public List<DentistSchedule> replaceSchedules(Long dentistId, List<ScheduleBlock> blocks) {
        dentists.findById(dentistId).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        List<DentistSchedule> parsed = new ArrayList<>();
        for (ScheduleBlock b : blocks) {
            if (b.weekday() < 1 || b.weekday() > 7) throw ApiException.badRequest("HORARIO_INVALIDO", "Día inválido.");
            LocalTime s, e;
            try {
                s = LocalTime.parse(b.start());
                e = LocalTime.parse(b.end());
            } catch (Exception ex) {
                throw ApiException.badRequest("HORARIO_INVALIDO", "Usa el formato HH:mm en los horarios.");
            }
            if (!e.isAfter(s)) throw ApiException.badRequest("HORARIO_INVALIDO", "La hora de fin debe ser posterior al inicio.");
            // Las citas empiezan en bloques de 15 minutos: un horario de 9:10 ofrecería horas que luego se rechazan.
            if (s.getMinute() % 15 != 0 || e.getMinute() % 15 != 0 || s.getSecond() != 0 || e.getSecond() != 0) {
                throw ApiException.badRequest("HORARIO_INVALIDO", "Usa horas en bloques de 15 minutos (por ejemplo 09:00, 09:15, 13:30).");
            }
            for (DentistSchedule p : parsed) {
                if (p.weekday == b.weekday() && p.startTime.isBefore(e) && p.endTime.isAfter(s)) {
                    throw ApiException.badRequest("HORARIO_INVALIDO", "Hay bloques que se superponen el mismo día.");
                }
            }
            parsed.add(new DentistSchedule(dentistId, b.weekday(), s, e));
        }
        // Las citas futuras no pueden quedar fuera del nuevo horario.
        List<Appointment> outside = new ArrayList<>();
        for (Appointment a : appointments.findPendingForDentist(dentistId, Dates.now(), AppointmentStatus.PENDING)) {
            int wd = a.startAt.getDayOfWeek().getValue();
            List<SlotCalculator.Block> day = new ArrayList<>();
            for (DentistSchedule p : parsed) if (p.weekday == wd) day.add(new SlotCalculator.Block(p.startTime, p.endTime));
            if (!SlotCalculator.fitsInBlocks(day, a.startAt.toLocalTime(), a.endAt.toLocalTime())) outside.add(a);
        }
        if (!outside.isEmpty()) {
            Appointment f = outside.get(0);
            throw ApiException.conflict("CITAS_FUERA_DE_HORARIO", outside.size() + " cita(s) pendiente(s) quedarían fuera del nuevo horario"
                    + " (la primera: " + f.startAt.format(DMY_HM) + " con " + f.patient.getFullName() + "). Reprográmalas antes de cambiar el horario.");
        }
        schedules.deleteByDentistId(dentistId);
        schedules.flush();
        schedules.saveAll(parsed);
        audit.record("ODONTOLOGO", dentistId, "HORARIO", parsed.size() + " bloques de atención");
        return schedules.findByDentistIdOrderByWeekdayAscStartTimeAsc(dentistId);
    }
}
