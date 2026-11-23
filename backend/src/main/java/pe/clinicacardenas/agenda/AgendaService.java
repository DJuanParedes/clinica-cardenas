package pe.clinicacardenas.agenda;

import pe.clinicacardenas.notification.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AgendaService {
    private static final String STARTAT = "startAt";


    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = new EnumMap<>(AppointmentStatus.class);
    static {
        TRANSITIONS.put(AppointmentStatus.PROGRAMADA, EnumSet.of(AppointmentStatus.CONFIRMADA, AppointmentStatus.EN_ESPERA,
                AppointmentStatus.CANCELADA, AppointmentStatus.NO_ASISTIO));
        TRANSITIONS.put(AppointmentStatus.CONFIRMADA, EnumSet.of(AppointmentStatus.EN_ESPERA,
                AppointmentStatus.CANCELADA, AppointmentStatus.NO_ASISTIO));
        TRANSITIONS.put(AppointmentStatus.EN_ESPERA, EnumSet.of(AppointmentStatus.EN_ATENCION, AppointmentStatus.CANCELADA));
        TRANSITIONS.put(AppointmentStatus.EN_ATENCION, EnumSet.of(AppointmentStatus.ATENDIDA));
    }

    private final AppointmentRepository appointments;
    private final DentistRepository dentists;
    private final DentistScheduleRepository schedules;
    private final PatientRepository patients;
    private final AuditService audit;
    private final NotificationService notifications;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AgendaService(AppointmentRepository appointments, DentistRepository dentists,
                         DentistScheduleRepository schedules, PatientRepository patients, AuditService audit,
                         NotificationService notifications, org.springframework.context.ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.events = events;
        this.appointments = appointments;
        this.dentists = dentists;
        this.schedules = schedules;
        this.patients = patients;
        this.audit = audit;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public record AppointmentRequest(Long patientId, Long dentistId, LocalDateTime startAt, Integer durationMin,
                                     String reason, String notes, String changeReason, String category) {
        public AppointmentRequest(Long patientId, Long dentistId, LocalDateTime startAt, Integer durationMin,
                                  String reason, String notes, String changeReason) {
            this(patientId, dentistId, startAt, durationMin, reason, notes, changeReason, null);
        }
    }

    public record StatusRequest(AppointmentStatus status, String reason) {}

    // ------------------------------------------------------------------ consultas

    public List<Dentist> activeDentists() {
        return dentists.findAvailable();
    }

    private List<SlotCalculator.Block> blocksFor(Long dentistId, LocalDate date) {
        List<SlotCalculator.Block> blocks = new ArrayList<>();
        for (DentistSchedule s : schedules.findByDentistIdAndWeekdayOrderByStartTimeAsc(dentistId, date.getDayOfWeek().getValue())) {
            blocks.add(new SlotCalculator.Block(s.startTime, s.endTime));
        }
        return blocks;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> slots(Long dentistId, LocalDate date, int durationMin, Long excludeId) {
        return slots(dentistId, date, durationMin, excludeId, null);
    }

    /**
     * Horarios del odontólogo. Si se indica el paciente, también se marcan como ocupadas las horas en las que
     * ese paciente ya tiene cita con otro odontólogo (no puede estar en dos consultorios a la vez).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> slots(Long dentistId, LocalDate date, int durationMin, Long excludeId, Long patientId) {
        Dentist d = dentists.findById(dentistId).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        checkDuration(durationMin);
        List<SlotCalculator.Busy> busy = new ArrayList<>();
        List<Appointment> dayAppts = appointments.findDentistOverlaps(d.id, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), AppointmentStatus.BLOCKING, excludeId);
        for (Appointment a : dayAppts) busy.add(new SlotCalculator.Busy(a.startAt, a.endAt));
        if (patientId != null) {
            for (Appointment a : appointments.findPatientOverlaps(patientId, date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay(), AppointmentStatus.BLOCKING, excludeId)) {
                busy.add(new SlotCalculator.Busy(a.startAt, a.endAt));
            }
        }
        List<SlotCalculator.Block> blocks = blocksFor(d.id, date);
        List<SlotCalculator.Slot> slots = SlotCalculator.compute(date, blocks, busy, durationMin, Dates.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dentist", d);
        m.put("date", date);
        m.put("durationMin", durationMin);
        m.put("works", !blocks.isEmpty());
        m.put("blocks", blocks);
        m.put("slots", slots);
        m.put("freeCount", slots.stream().filter(SlotCalculator.Slot::available).count());
        return m;
    }

    /** Agenda diaria: odontólogos que atienden ese día con sus bloques y todas las citas. */
    @Transactional(readOnly = true)
    public Map<String, Object> day(LocalDate date, Long dentistId) {
        List<Map<String, Object>> cols = new ArrayList<>();
        for (Dentist d : dentists.findAvailable()) {
            if (dentistId != null && !d.id.equals(dentistId)) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("dentist", d);
            c.put("blocks", blocksFor(d.id, date));
            cols.add(c);
        }
        List<Appointment> list = appointments.findInRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), dentistId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AppointmentStatus s : AppointmentStatus.values()) counts.put(s.name(), 0L);
        for (Appointment a : list) counts.merge(a.status.name(), 1L, Long::sum);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("columns", cols);
        m.put("appointments", list);
        m.put("counts", counts);
        return m;
    }

    @Transactional(readOnly = true)
    public List<Appointment> range(LocalDate from, LocalDate to, Long dentistId) {
        if (to.isBefore(from)) throw pe.clinicacardenas.common.ApiException.field("to", "La fecha final debe ser posterior a la inicial.");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 62) throw pe.clinicacardenas.common.ApiException.field("to", "Consulta como máximo dos meses a la vez.");
        return appointments.findInRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), dentistId);
    }

    public Appointment get(Long id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("La cita"));
    }

    // ------------------------------------------------------------------ HU-07 programación sin cruces

    private static void checkDuration(int durationMin) {
        if (durationMin < 15 || durationMin > 240 || durationMin % 15 != 0) {
            throw ApiException.field("durationMin", "La duración debe ser múltiplo de 15 minutos (15 a 240).");
        }
    }

    private Appointment validateAndFill(Appointment a, AppointmentRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.patientId() == null) errors.put("patientId", "Selecciona un paciente.");
        if (r.dentistId() == null) errors.put("dentistId", "Selecciona un odontólogo.");
        if (r.startAt() == null) errors.put(STARTAT, "Selecciona fecha y hora.");
        if (r.reason() == null || r.reason().isBlank()) errors.put("reason", "Indica el motivo de la cita.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        }
        int duration = r.durationMin() == null ? 30 : r.durationMin();
        checkDuration(duration);

        // Bloqueo pesimista sobre el odontólogo: dos recepcionistas no pueden tomar el mismo horario a la vez.
        Dentist d = dentists.lockById(r.dentistId()).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        // También se bloquea al paciente: no puede quedar con dos citas cruzadas aunque sean con distintos odontólogos.
        Patient p = patients.lockById(r.patientId()).orElseThrow(() -> ApiException.notFound("El paciente"));
        if (!p.active) throw ApiException.badRequest("PACIENTE_INACTIVO", "El paciente está inactivo.");
        if (!d.active || !d.user.active) throw ApiException.badRequest("ODONTOLOGO_INACTIVO", "El odontólogo no está disponible.");

        LocalDateTime start = r.startAt().withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(duration);
        if (start.isBefore(Dates.now())) {
            throw ApiException.field(STARTAT, "No se pueden programar citas en horas pasadas.");
        }
        if (start.getMinute() % 15 != 0) {
            throw ApiException.field(STARTAT, "La hora de inicio debe ser en bloques de 15 minutos.");
        }
        List<SlotCalculator.Block> blocks = blocksFor(d.id, start.toLocalDate());
        if (!SlotCalculator.fitsInBlocks(blocks, start.toLocalTime(), end.toLocalTime())) {
            throw ApiException.conflict("FUERA_DE_HORARIO",
                    d.getFullName() + " no atiende de " + start.format(HM) + " a " + end.format(HM) + " ese día.");
        }
        List<Appointment> clash = appointments.findDentistOverlaps(d.id, start, end, AppointmentStatus.BLOCKING, a.id);
        if (!clash.isEmpty()) {
            Appointment c = clash.get(0);
            throw ApiException.conflict("CRUCE_HORARIO",
                    d.getFullName() + " ya tiene una cita de " + c.startAt.format(HM) + " a " + c.endAt.format(HM)
                            + ". Elige otro horario libre.");
        }
        List<Appointment> patientClash = appointments.findPatientOverlaps(p.id, start, end, AppointmentStatus.BLOCKING, a.id);
        if (!patientClash.isEmpty()) {
            Appointment c = patientClash.get(0);
            throw ApiException.conflict("CRUCE_PACIENTE",
                    p.getFullName() + " ya tiene una cita con " + c.dentist.getFullName() + " de "
                            + c.startAt.format(HM) + " a " + c.endAt.format(HM) + ".");
        }
        a.patient = p;
        a.dentist = d;
        a.startAt = start;
        a.endAt = end;
        a.durationMin = duration;
        a.reason = r.reason().trim();
        a.notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        // v2.10: la categoría es la misma de los procedimientos y de las reglas de comisión. Si no llega, se deduce del motivo.
        a.category = categoryFor(r.category(), a.reason);
        return a;
    }

    private String categoryFor(String category, String reason) {
        if (category != null && !category.isBlank()) {
            List<String> l = jdbc.queryForList("select category from procedures where category = ? limit 1", String.class, category.trim());
            if (l.isEmpty()) throw ApiException.field("category", "La categoría no existe.");
            return l.get(0);
        }
        if (reason == null || reason.isBlank()) return null;
        List<String> l = jdbc.queryForList("select category from procedures where active = true and category is not null"
                + " and (? like concat('%', name, '%') or ? = category) order by length(name) desc limit 1", String.class, reason, reason);
        if (!l.isEmpty()) return l.get(0);
        // Palabras de uso diario (las mismas que se usaron para clasificar las citas antiguas en V24).
        String r = java.text.Normalizer.normalize(reason.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String[][] words = {
                {"Ortodoncia", "ortodon", "bracket", "brackets", "alineador"}, {"Endodoncia", "endodon", "conducto", "nervio"},
                {"Prevención", "profila", "limpieza", "fluor", "sellante", "destartraje"}, {"Cirugía", "exodon", "extrac", "cordal", "muela del juicio"},
                {"Operatoria", "restaura", "resina", "curacion", "caries", "obturac"}, {"Estética", "blanquea", "carilla"},
                {"Implantología", "implant"}, {"Prótesis", "protesis", "corona", "puente", "placa"},
                {"Periodoncia", "encia", "periodon", "sangrado"}, {"Diagnóstico", "evaluaci", "control", "dolor", "consulta", "diagnost", "radiograf", "revision"}};
        for (String[] w : words) for (int i = 1; i < w.length; i++) if (r.contains(w[i])) {
            List<String> ok = jdbc.queryForList("select category from procedures where category = ? limit 1", String.class, w[0]);
            if (!ok.isEmpty()) return ok.get(0);
        }
        return null;
    }

    /*
     * READ_COMMITTED: tras obtener el bloqueo del odontólogo, la verificación de cruces debe ver las citas
     * que otra transacción acaba de confirmar. Con REPEATABLE READ (por defecto en MySQL) se leería una foto
     * anterior y dos reservas simultáneas del mismo horario podrían aceptarse.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment create(AppointmentRequest r) {
        Appointment a = validateAndFill(new Appointment(), r);
        a.status = AppointmentStatus.PROGRAMADA;
        a.createdAt = Dates.now();
        a.createdBy = CurrentUser.usernameOrSystem();
        appointments.save(a);
        audit.record("CITA", a.id, "CREAR", a.patient.getFullName() + " con " + a.dentist.getFullName()
                + " el " + a.startAt.format(DMY_HM));
        notifications.appointmentCreated(a);   // HU-23: recordatorio programado
        events.publishEvent(new AppointmentEvent(a, AppointmentEvent.Type.CREATED));
        return a;
    }

    /** HU-08: reprogramar revalidando disponibilidad y registrando el motivo. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment reschedule(Long id, AppointmentRequest r) {
        Appointment a = get(id);
        if (a.status.isFinal() || a.status == AppointmentStatus.EN_ATENCION) {
            throw ApiException.badRequest("CITA_CERRADA", "Solo se pueden reprogramar citas pendientes.");
        }
        if (r.changeReason() == null || r.changeReason().isBlank()) {
            throw ApiException.field("changeReason", "Indica el motivo de la reprogramación.");
        }
        String before = a.startAt.format(DMY_HM) + " con " + a.dentist.getFullName();
        java.time.LocalDateTime previousStart = a.startAt;
        String previousDentist = a.dentist.getFullName();
        AppointmentRequest merged = new AppointmentRequest(a.patient.id, r.dentistId(), r.startAt(), r.durationMin(),
                r.reason() == null ? a.reason : r.reason(), r.notes(), r.changeReason(), r.category() == null ? a.category : r.category());
        validateAndFill(a, merged);
        a.status = AppointmentStatus.PROGRAMADA;
        a.statusReason = "Reprogramada: " + r.changeReason().trim();
        a.rescheduleRequestedAt = null;
        a.arrivedAt = null;   // si había llegado y se reprograma, la llegada ya no aplica a la nueva hora
        a.updatedAt = Dates.now();
        a.updatedBy = CurrentUser.usernameOrSystem();
        appointments.save(a);
        audit.record("CITA", a.id, "REPROGRAMAR", "De " + before + " a " + a.startAt.format(DMY_HM)
                + " · " + r.changeReason().trim());
        if (!previousStart.equals(a.startAt) || !previousDentist.equals(a.dentist.getFullName())) {
            notifications.appointmentRescheduled(a, previousStart, previousDentist);   // HU-24
            events.publishEvent(new AppointmentEvent(a, AppointmentEvent.Type.RESCHEDULED));
        }
        return a;
    }

    /** HU-09: llegada y estado de atención; cancelaciones con motivo. */
    @Transactional
    public Appointment changeStatus(Long id, StatusRequest r) {
        Appointment a = get(id);
        if (r.status() == null) throw ApiException.field("status", "Indica el nuevo estado.");
        Set<AppointmentStatus> allowed = TRANSITIONS.getOrDefault(a.status, EnumSet.noneOf(AppointmentStatus.class));
        if (!allowed.contains(r.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSICION_INVALIDA",
                    "Una cita " + label(a.status) + " no puede pasar a " + label(r.status()) + ".");
        }
        if (r.status() == AppointmentStatus.CANCELADA && (r.reason() == null || r.reason().isBlank())) {
            throw ApiException.field("reason", "Indica el motivo de la cancelación.");
        }
        checkTiming(a, r.status(), Dates.now());
        if (r.status() == AppointmentStatus.EN_ESPERA) {
            a.arrivedAt = Dates.now();
        }
        AppointmentStatus prev = a.status;
        a.status = r.status();
        a.statusReason = r.reason() == null || r.reason().isBlank() ? a.statusReason : r.reason().trim();
        a.updatedAt = Dates.now();
        a.updatedBy = CurrentUser.usernameOrSystem();
        appointments.save(a);
        audit.record("CITA", a.id, "ESTADO", label(prev) + " → " + label(a.status)
                + (r.reason() == null || r.reason().isBlank() ? "" : " · " + r.reason().trim()));
        if (a.status == AppointmentStatus.CANCELADA) {
            notifications.appointmentCancelled(a, r.reason());   // HU-24
        }
        if (a.status == AppointmentStatus.ATENDIDA) {
            notifications.appointmentAttended(a);                 // v2.6: agradecimiento y encuesta
        }
        events.publishEvent(new AppointmentEvent(a, AppointmentEvent.Type.STATUS));
        return a;
    }

    /** Coherencia con el reloj: no se registra una llegada de otro día ni una inasistencia antes de la hora. */
    static void checkTiming(Appointment a, AppointmentStatus to, LocalDateTime now) {
        String dia = a.startAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        switch (to) {
            case EN_ESPERA, EN_ATENCION -> {
                if (!a.startAt.toLocalDate().equals(now.toLocalDate())) {
                    throw ApiException.conflict("FUERA_DE_FECHA",
                            "La llegada y la atención solo se registran el día de la cita (" + dia + ").");
                }
            }
            case NO_ASISTIO -> {
                if (now.isBefore(a.startAt)) {
                    throw ApiException.conflict("CITA_NO_INICIADA",
                            "La cita aún no empieza (" + a.startAt.format(DMY_HM) + "): no se puede marcar como inasistencia.");
                }
            }
            case CONFIRMADA -> {
                if (!now.isBefore(a.endAt)) {
                    throw ApiException.conflict("CITA_PASADA", "La cita ya pasó: no se puede confirmar.");
                }
            }
            default -> { }
        }
    }

    private static String label(AppointmentStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }
}
