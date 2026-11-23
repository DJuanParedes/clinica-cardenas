package pe.clinicacardenas.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.messaging.MessageTemplateService;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.ConsentService;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * v2.7: segmentos de pacientes (filtros guardados), tareas en lote desde un segmento, saludos de cumpleaños y
 * consentimiento de publicidad.
 */
@Service
public class MarketingService {
    private static final String CUMPLEANOS = "CUMPLEANOS";
    private static final String EL_SEGMENTO = "El segmento";
    private static final String SEGMENTO = "SEGMENTO";
    private static final String ACTIVE = "active";
    private static final String CUMPLEANOS_ACTIVO = "cumpleanos_activo";
    private static final String CUMPLEANOS_HORA = "cumpleanos_hora";
    private static final String CUMPLEANOS_TAREA = "cumpleanos_tarea";
    private static final String DESCRIPTION = "description";
    private static final String PHONE = "phone";


    /** Proxy de Spring de esta misma clase: las llamadas internas por aquí sí respetan @Transactional. */
    private final org.springframework.beans.factory.ObjectProvider<MarketingService> selfProvider;

    private MarketingService self() {
        return selfProvider.getObject();
    }


    private static final Logger log = LoggerFactory.getLogger(MarketingService.class);
    public static final int MAX_BULK_TASKS = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;
    private final TaskService tasks;
    private final PatientInsightsService insights;
    private final PatientRepository patients;
    private final NotificationService notifications;
    private final MessageTemplateService templates;
    private final ConsentService consents;

    public MarketingService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit, TaskService tasks, PatientInsightsService insights,
                            PatientRepository patients, NotificationService notifications, MessageTemplateService templates, ConsentService consents,
                            org.springframework.beans.factory.ObjectProvider<MarketingService> selfProvider) {
        this.selfProvider = selfProvider;
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.tasks = tasks;
        this.insights = insights;
        this.patients = patients;
        this.notifications = notifications;
        this.templates = templates;
        this.consents = consents;
    }

    public record SegmentRequest(String name, String description, SegmentFilter filters) {}

    public record BulkTaskRequest(SegmentFilter filters, Long segmentId, String kind, String title, String notes, Long assigneeId,
                                  LocalDate dueDate, String dueTime, String priority) {}

    public record ConsentRequest(Boolean accepted, String channel) {}

    public record BirthdaySettings(Boolean active, String time, Boolean createTask) {}

    // ------------------------------------------------------------------ segmentos

    public List<Long> ids(SegmentFilter f) {
        var w = (f == null ? SegmentFilter.empty() : f).where(Dates.now());
        return jdbc.queryForList("select p.id from patients p where " + w.sql() + " order by p.last_name, p.first_name", Long.class, w.args().toArray());
    }

    /** Cuántos pacientes, cuántos pueden recibir una campaña y una muestra con sus datos de seguimiento. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(SegmentFilter raw) {
        SegmentFilter f = (raw == null ? SegmentFilter.empty() : raw).clean();
        List<Long> ids = ids(f);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(DESCRIPTION, f.describe());
        out.put("count", ids.size());
        out.put("reachable", reachable(ids).size());
        List<Long> sample = ids.subList(0, Math.min(20, ids.size()));
        Map<Long, Map<String, Object>> ins = insights.forPatients(sample);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!sample.isEmpty()) {
            Map<Long, Map<String, Object>> base = new HashMap<>();
            jdbc.query("select id, first_name, last_name, phone, email, birth_date, source, marketing_consent, hc_number from patients where id in ("
                    + String.join(",", Collections.nCopies(sample.size(), "?")) + ")", rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong(1));
                m.put("fullName", rs.getString(2) + " " + rs.getString(3));
                m.put(PHONE, rs.getString(4));
                m.put("email", rs.getString(5));
                m.put("birthDate", rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate());
                m.put("source", rs.getString(7));
                Object mc = rs.getObject(8);
                m.put("marketingConsent", mc == null ? null : rs.getBoolean(8));
                m.put("hcNumber", rs.getString(9));
                base.put(rs.getLong(1), m);
            }, sample.toArray());
            for (Long id : sample) {
                Map<String, Object> m = base.get(id);
                if (m == null) continue;
                m.put("insights", ins.get(id));
                rows.add(m);
            }
        }
        out.put("sample", rows);
        return out;
    }

    /** Pacientes del grupo que pueden recibir publicidad: aceptaron, no son de demostración y tienen un canal. */
    public List<Long> reachable(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 900) {
            List<Long> part = ids.subList(i, Math.min(ids.size(), i + 900));
            out.addAll(jdbc.queryForList("select id from patients where marketing_consent = true and demo = false "
                    + "and coalesce(notify_channel, 'WHATSAPP') <> 'NINGUNO' and (coalesce(phone, '') <> '' or coalesce(email, '') <> '') and id in ("
                    + String.join(",", Collections.nCopies(part.size(), "?")) + ")", Long.class, part.toArray()));
        }
        return out;
    }

    public List<Map<String, Object>> segments() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select id, name, description, filters, created_by, created_at, updated_at from segments order by name")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put(DESCRIPTION, r.get(DESCRIPTION));
            SegmentFilter f = parse(r.get("filters"));
            m.put("filters", f);
            m.put("summary", f.describe());
            List<Long> ids = ids(f);
            m.put("count", ids.size());
            m.put("reachable", reachable(ids).size());
            m.put("createdBy", r.get("created_by"));
            m.put("createdAt", r.get("created_at"));
            m.put("updatedAt", r.get("updated_at"));
            out.add(m);
        }
        return out;
    }

    public SegmentFilter segmentFilter(Long id) {
        List<Object> l = jdbc.queryForList("select filters from segments where id = ?", Object.class, id);
        if (l.isEmpty()) throw ApiException.notFound(EL_SEGMENTO);
        return parse(l.get(0));
    }

    public String segmentName(Long id) {
        List<String> l = jdbc.queryForList("select name from segments where id = ?", String.class, id);
        return l.isEmpty() ? null : l.get(0);
    }

    @Transactional
    public Map<String, Object> saveSegment(Long id, SegmentRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.name() == null ? "" : r.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 80) e.put("name", "Ponle un nombre de 3 a 80 caracteres.");
        else if (jdbc.queryForObject("select count(*) from segments where lower(name) = lower(?) and id <> ?", Long.class, name, id == null ? -1L : id) > 0)
            e.put("name", "Ya existe un segmento con ese nombre.");
        String desc = r.description() == null || r.description().isBlank() ? null : r.description().trim();
        if (desc != null && desc.length() > 300) e.put(DESCRIPTION, "Máximo 300 caracteres.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del segmento.", e);
        SegmentFilter f = (r.filters() == null ? SegmentFilter.empty() : r.filters()).clean();
        String js = write(f);
        String by = CurrentUser.usernameOrSystem();
        if (id == null) {
            jdbc.update("insert into segments (name, description, filters, created_by, created_at) values (?, ?, ?, ?, ?)", name, desc, js, by, Dates.now());
            id = jdbc.queryForObject("select id from segments where name = ?", Long.class, name);
            audit.register(SEGMENTO, id, "CREAR", name + " · " + f.describe());
        } else {
            if (jdbc.update("update segments set name = ?, description = ?, filters = ?, updated_by = ?, updated_at = ? where id = ?", name, desc, js, by, Dates.now(), id) == 0)
                throw ApiException.notFound(EL_SEGMENTO);
            audit.register(SEGMENTO, id, "EDITAR", name + " · " + f.describe());
        }
        final Long sid = id;
        return segments().stream().filter(s -> sid.equals(((Number) s.get("id")).longValue())).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteSegment(Long id) {
        String name = segmentName(id);
        if (name == null) throw ApiException.notFound(EL_SEGMENTO);
        jdbc.update("delete from segments where id = ?", id);
        audit.register(SEGMENTO, id, "ELIMINAR", name);
    }

    /** Crea la misma tarea para cada paciente del segmento (sin repetirla a quien ya la tiene pendiente). */
    @Transactional
    public Map<String, Object> bulkTasks(BulkTaskRequest r) {
        SegmentFilter f = r.segmentId() != null ? segmentFilter(r.segmentId()) : (r.filters() == null ? SegmentFilter.empty() : r.filters()).clean();
        String ref = r.segmentId() != null ? segmentName(r.segmentId()) : f.describe();
        var base = tasks.validate(new TaskService.TaskRequest(null, null, r.kind(), r.title(), r.notes(), r.assigneeId(), r.dueDate(), r.dueTime(), r.priority()), true);
        List<Long> ids = ids(f);
        if (ids.isEmpty()) throw ApiException.badRequest("SEGMENTO_VACIO", "El segmento no tiene pacientes.");
        if (ids.size() > MAX_BULK_TASKS)
            throw ApiException.badRequest("SEGMENTO_GRANDE", "Son " + ids.size() + " pacientes: afina el filtro (máximo " + MAX_BULK_TASKS + " tareas a la vez).");
        int created = 0;
        int skipped = 0;
        String by = CurrentUser.usernameOrSystem();
        for (Long pid : ids) {
            if (jdbc.queryForObject("select count(*) from tasks where patient_id = ? and status = 'PENDIENTE' and title = ?", Long.class, pid, base.title()) > 0) {
                skipped++;
                continue;
            }
            tasks.insert(new TaskService.NewTask(pid, null, base.kind(), base.title(), base.notes(), base.assigneeId(), base.dueDate(), base.dueTime(),
                    base.priority(), SEGMENTO, ref == null ? null : ref.substring(0, Math.min(150, ref.length()))), by);
            created++;
        }
        audit.register(SEGMENTO, r.segmentId(), "TAREAS", created + " tareas \"" + base.title() + "\" · " + ref);
        return Map.of("created", created, "skipped", skipped);
    }

    // ------------------------------------------------------------------ consentimiento de publicidad

    @Transactional
    public Patient setConsent(Long patientId, ConsentRequest r) {
        Patient p = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        if (r.accepted() == null) throw ApiException.field("accepted", "Indica si acepta o no.");
        String channel = r.channel() == null || r.channel().isBlank() ? "PRESENCIAL" : r.channel().trim().toUpperCase();
        if (!Set.of("PRESENCIAL", "TELEFONO", "WHATSAPP", "WEB", "APP").contains(channel)) throw ApiException.field("channel", "Canal no válido.");
        applyConsent(p, r.accepted(), channel, CurrentUser.usernameOrSystem(), null);
        return p;
    }

    /** Registra la aceptación (con la versión del texto) o la baja, y deja constancia en la auditoría. */
    public void applyConsent(Patient p, boolean accepted, String channel, String by, String ip) {
        if (accepted) consents.register(p, "PUBLICIDAD", channel, by, ip);
        p.marketingConsent = accepted;
        p.marketingConsentAt = Dates.now();
        patients.save(p);
        audit.register("PACIENTE", p.id, accepted ? "PUBLICIDAD_ACEPTA" : "PUBLICIDAD_BAJA", (accepted ? "Acepta" : "No acepta / baja de") + " promociones · " + channel);
    }

    // ------------------------------------------------------------------ cumpleaños

    public Map<String, Object> birthdaySettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(ACTIVE, "true".equals(setting(CUMPLEANOS_ACTIVO)));
        m.put("time", Optional.ofNullable(setting(CUMPLEANOS_HORA)).orElse("09:00"));
        m.put("createTask", "true".equals(setting(CUMPLEANOS_TAREA)));
        m.put("template", templates.get(CUMPLEANOS));
        return m;
    }

    @Transactional
    public Map<String, Object> updateBirthdaySettings(BirthdaySettings s) {
        if (s.time() != null) {
            LocalTime t;
            try { t = LocalTime.parse(s.time().substring(0, Math.min(5, s.time().length()))); }
            catch (Exception e) { throw ApiException.field("time", "Hora no válida."); }
            if (t.isBefore(LocalTime.of(7, 0)) || t.isAfter(LocalTime.of(20, 0))) throw ApiException.field("time", "Elige una hora entre 07:00 y 20:00.");
            put(CUMPLEANOS_HORA, t.toString());
        }
        if (s.active() != null) put(CUMPLEANOS_ACTIVO, String.valueOf(s.active()));
        if (s.createTask() != null) put(CUMPLEANOS_TAREA, String.valueOf(s.createTask()));
        audit.register("MARKETING", null, CUMPLEANOS, "Saludo " + (Boolean.TRUE.equals(s.active()) ? "activo" : "inactivo") + " · " + setting(CUMPLEANOS_HORA)
                + (Boolean.TRUE.equals(s.createTask()) ? " · con tarea de llamada" : ""));
        return birthdaySettings();
    }

    /** Cumpleaños del mes con si ya se saludó este año. */
    public List<Map<String, Object>> birthdays(int month) {
        if (month < 1 || month > 12) throw ApiException.field("month", "Mes no válido.");
        int year = Dates.today().getYear();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select p.id, p.first_name, p.last_name, p.birth_date, p.phone, p.email, p.marketing_consent, p.notify_channel, p.demo,
                       (select count(*) from notifications n where n.idempotency_key like concat('cumple:', p.id, ':', ?, ':%')) as greeted,
                       (select count(*) from tasks t where t.patient_id = p.id and t.origin = 'CUMPLEANOS' and t.origin_ref = ?) as tasked
                  from patients p
                 where p.active = true and month(p.birth_date) = ?
                 order by day(p.birth_date), p.last_name
                """, year, String.valueOf(year), month)) {
            LocalDate bd = Dates.toLocalDate(r.get("birth_date"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("fullName", r.get("first_name") + " " + r.get("last_name"));
            m.put("birthDate", bd);
            m.put("day", bd.getDayOfMonth());
            m.put("turns", year - bd.getYear());
            m.put(PHONE, r.get(PHONE));
            Object mc = r.get("marketing_consent");
            m.put("marketingConsent", mc == null ? null : (mc instanceof Boolean b ? b : ((Number) mc).intValue() != 0));
            m.put("greeted", ((Number) r.get("greeted")).longValue() > 0);
            m.put("tasked", ((Number) r.get("tasked")).longValue() > 0);
            out.add(m);
        }
        return out;
    }

    /** Revisa cada 15 minutos (7:00 a 21:00) si ya es la hora del saludo. Las claves evitan repetirlo. */
    @Scheduled(cron = "0 */15 7-21 * * *", zone = "America/Lima")
    public void birthdayTick() {
        try {
            LocalTime at = LocalTime.parse(Optional.ofNullable(setting(CUMPLEANOS_HORA)).orElse("09:00"));
            if (Dates.now().toLocalTime().isBefore(at)) return;
            self().runBirthdays(false);
        } catch (Exception e) {
            log.warn("Saludos de cumpleaños: {}", e.getMessage());
        }
    }

    /** Envía los saludos y crea las tareas de hoy según la configuración. {@code manual}: lo pidió administración. */
    @Transactional
    public Map<String, Object> runBirthdays(boolean manual) {
        boolean send = "true".equals(setting(CUMPLEANOS_ACTIVO));
        boolean task = "true".equals(setting(CUMPLEANOS_TAREA));
        int sent = 0;
        int created = 0;
        if (!send && !task) return Map.of("sent", 0, "tasks", 0, ACTIVE, false);
        LocalDate today = Dates.today();
        int year = today.getYear();
        // los nacidos un 29 de febrero se saludan el 28 cuando el año no es bisiesto
        boolean feb28 = today.getMonthValue() == 2 && today.getDayOfMonth() == 28 && !today.isLeapYear();
        List<Long> ids = jdbc.queryForList("select id from patients where active = true and demo = false and ((month(birth_date) = ? and day(birth_date) = ?)"
                + (feb28 ? " or (month(birth_date) = 2 and day(birth_date) = 29)" : "") + ")", Long.class, today.getMonthValue(), today.getDayOfMonth());
        var tpl = templates.get(CUMPLEANOS);
        for (Long id : ids) {
            Patient p = patients.findById(id).orElse(null);
            if (p == null) continue;
            if (send && !Boolean.FALSE.equals(p.marketingConsent)) {
                sent += notifications.marketing(p, CUMPLEANOS, CUMPLEANOS, (String) tpl.get("subject"), (String) tpl.get("body"),
                        "PREFERIDO", "cumple:" + p.id + ":" + year).size();
            }
            if (task && jdbc.queryForObject("select count(*) from tasks where patient_id = ? and origin = 'CUMPLEANOS' and origin_ref = ?",
                    Long.class, p.id, String.valueOf(year)) == 0) {
                tasks.insert(new TaskService.NewTask(p.id, null, "LLAMAR", "Saludar por su cumpleaños", null, null, today, null, "NORMAL",
                        CUMPLEANOS, String.valueOf(year)), "sistema");
                created++;
            }
        }
        if (sent + created > 0 || manual) log.info("Cumpleaños de hoy: {} saludos en cola, {} tareas", sent, created);
        return Map.of("sent", sent, "tasks", created, ACTIVE, send, "birthdays", ids.size());
    }

    // ------------------------------------------------------------------ utilidades

    String setting(String k) {
        List<String> l = jdbc.queryForList("select v from app_settings where k = ?", String.class, k);
        return l.isEmpty() ? null : l.get(0);
    }

    private void put(String k, String v) {
        if (jdbc.update("update app_settings set v = ?, updated_at = ?, updated_by = ? where k = ?", v, Dates.now(), CurrentUser.usernameOrSystem(), k) == 0)
            jdbc.update("insert into app_settings (k, v, updated_at, updated_by) values (?, ?, ?, ?)", k, v, Dates.now(), CurrentUser.usernameOrSystem());
    }

    SegmentFilter parse(Object raw) {
        try { return raw == null ? SegmentFilter.empty() : json.readValue(raw.toString(), SegmentFilter.class); }
        catch (Exception e) { return SegmentFilter.empty(); }
    }

    String write(SegmentFilter f) {
        try { return json.writeValueAsString(f); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static LocalDateTime now() { return Dates.now(); }
}
