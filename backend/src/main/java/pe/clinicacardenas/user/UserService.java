package pe.clinicacardenas.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Dentist;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.agenda.DentistSchedule;
import pe.clinicacardenas.agenda.DentistScheduleRepository;
import pe.clinicacardenas.auth.PasswordPolicy;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,40}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String[] COLORS = {"#00A3D4", "#F08A00", "#1E5FAE", "#7A4FC9", "#217A4A", "#C2410C"};

    private final UserRepository users;
    private final DentistRepository dentists;
    private final DentistScheduleRepository schedules;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, DentistRepository dentists, DentistScheduleRepository schedules,
                       PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.dentists = dentists;
        this.schedules = schedules;
        this.encoder = encoder;
        this.audit = audit;
    }

    public record UserRequest(String username, String email, String fullName, Role role, String password,
                              String specialty, String cop) {}

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
        u.updatedAt = Dates.now();
        u.updatedBy = CurrentUser.usernameOrSystem();
        users.save(u);
        Optional<Dentist> dentist = dentists.findByUserId(u.id);
        if (u.role == Role.ODONTOLOGO) {
            ensureDentist(u, r.specialty(), r.cop());
        } else {
            dentist.ifPresent(d -> { d.active = false; dentists.save(d); });
        }
        audit.record("USUARIO", u.id, "EDITAR", changes.isEmpty() ? "Datos revisados sin cambios" : String.join(", ", changes));
        return view(u);
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
            for (DentistSchedule p : parsed) {
                if (p.weekday == b.weekday() && p.startTime.isBefore(e) && p.endTime.isAfter(s)) {
                    throw ApiException.badRequest("HORARIO_INVALIDO", "Hay bloques que se superponen el mismo día.");
                }
            }
            parsed.add(new DentistSchedule(dentistId, b.weekday(), s, e));
        }
        schedules.deleteByDentistId(dentistId);
        schedules.flush();
        schedules.saveAll(parsed);
        audit.record("ODONTOLOGO", dentistId, "HORARIO", parsed.size() + " bloques de atención");
        return schedules.findByDentistIdOrderByWeekdayAscStartTimeAsc(dentistId);
    }
}
