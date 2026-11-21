package pe.clinicacardenas.crm;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;
import pe.clinicacardenas.security.PermissionService;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * v2.7: tareas de seguimiento (llamar, cobrar, recordar…) con responsable y fecha.
 * Pueden ser de un paciente, de un lead del embudo o generales. Se crean a mano, desde un segmento,
 * por cumpleaños o desde un lead.
 */
@Service
public class TaskService {

    public static final Map<String, String> KINDS = new LinkedHashMap<>();
    static {
        KINDS.put("LLAMAR", "Llamar");
        KINDS.put("WHATSAPP", "Escribir por WhatsApp");
        KINDS.put("COBRAR", "Cobrar");
        KINDS.put("RECORDAR", "Recordar");
        KINDS.put("OTRO", "Otro");
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PermissionService permissions;

    public TaskService(JdbcTemplate jdbc, AuditService audit, PermissionService permissions) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.permissions = permissions;
    }

    public record TaskRequest(Long patientId, Long leadId, String kind, String title, String notes, Long assigneeId,
                              LocalDate dueDate, String dueTime, String priority) {}

    public record CloseRequest(String result) {}

    /** Datos ya validados para insertar (también los usan segmentos, cumpleaños y leads). */
    public record NewTask(Long patientId, Long leadId, String kind, String title, String notes, Long assigneeId,
                          LocalDate dueDate, LocalTime dueTime, String priority, String origin, String originRef) {}

    // ------------------------------------------------------------------ consultas

    private static final String SELECT = """
            select t.id, t.kind, t.title, t.notes, t.due_date, t.due_time, t.priority, t.status, t.result, t.origin, t.origin_ref,
                   t.created_by, t.created_at, t.completed_at, t.completed_by, t.assignee_id, u.full_name as assignee_name,
                   t.patient_id, concat(p.first_name, ' ', p.last_name) as patient_name, p.phone as patient_phone, p.hc_number,
                   t.lead_id, l.full_name as lead_name, l.phone as lead_phone, l.stage as lead_stage
              from tasks t
              left join users u on u.id = t.assignee_id
              left join patients p on p.id = t.patient_id
              left join leads l on l.id = t.lead_id
            """;

    public Long currentUserId() {
        List<Long> ids = jdbc.queryForList("select id from users where username = ?", Long.class, CurrentUser.usernameOrSystem());
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String scope, String status, String kind, Long assigneeId, Long patientId, Long leadId, String q) {
        Long me = currentUserId();
        List<String> c = new ArrayList<>();
        List<Object> a = new ArrayList<>();
        String st = status == null || status.isBlank() ? "PENDIENTE" : status.trim().toUpperCase();
        if (!"TODAS".equals(st)) { c.add("t.status = ?"); a.add(st); }
        if (patientId != null) { c.add("t.patient_id = ?"); a.add(patientId); }
        if (leadId != null) { c.add("t.lead_id = ?"); a.add(leadId); }
        if (patientId == null && leadId == null) {
            String sc = scope == null ? "mine" : scope;
            switch (sc) {
                case "mine" -> { c.add("t.assignee_id = ?"); a.add(me == null ? -1L : me); }
                case "unassigned" -> c.add("t.assignee_id is null");
                default -> { }
            }
        }
        if (assigneeId != null) { c.add("t.assignee_id = ?"); a.add(assigneeId); }
        if (kind != null && !kind.isBlank()) { c.add("t.kind = ?"); a.add(kind.trim().toUpperCase()); }
        if (q != null && !q.isBlank()) {
            c.add("(lower(t.title) like ? or lower(concat(coalesce(p.first_name, ''), ' ', coalesce(p.last_name, ''))) like ? or lower(coalesce(l.full_name, '')) like ?)");
            String like = "%" + q.trim().toLowerCase() + "%";
            a.add(like); a.add(like); a.add(like);
        }
        String order = "PENDIENTE".equals(st) ? " order by t.due_date, t.due_time is null, t.due_time, t.priority = 'ALTA' desc, t.id"
                : " order by coalesce(t.completed_at, t.created_at) desc";
        String sql = SELECT + (c.isEmpty() ? "" : " where " + String.join(" and ", c)) + order + " limit 500";
        List<Map<String, Object>> items = jdbc.query(sql, (rs, i) -> row(rs), a.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("counts", counts(me));
        return out;
    }

    /** Contadores para el menú y el inicio: mis tareas vencidas, de hoy, próximas y sin asignar. */
    public Map<String, Object> counts(Long me) {
        LocalDate today = Dates.today();
        Map<String, Object> m = new LinkedHashMap<>();
        Long uid = me == null ? -1L : me;
        m.put("mineOverdue", jdbc.queryForObject("select count(*) from tasks where status = 'PENDIENTE' and assignee_id = ? and due_date < ?", Long.class, uid, today));
        m.put("mineToday", jdbc.queryForObject("select count(*) from tasks where status = 'PENDIENTE' and assignee_id = ? and due_date = ?", Long.class, uid, today));
        m.put("mineUpcoming", jdbc.queryForObject("select count(*) from tasks where status = 'PENDIENTE' and assignee_id = ? and due_date > ?", Long.class, uid, today));
        m.put("unassigned", jdbc.queryForObject("select count(*) from tasks where status = 'PENDIENTE' and assignee_id is null", Long.class));
        m.put("allOverdue", jdbc.queryForObject("select count(*) from tasks where status = 'PENDIENTE' and due_date < ?", Long.class, today));
        return m;
    }

    public Map<String, Object> summary() { return counts(currentUserId()); }

    private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("kind", rs.getString("kind"));
        m.put("kindLabel", KINDS.getOrDefault(rs.getString("kind"), rs.getString("kind")));
        m.put("title", rs.getString("title"));
        m.put("notes", rs.getString("notes"));
        LocalDate due = rs.getDate("due_date").toLocalDate();
        m.put("dueDate", due);
        m.put("dueTime", rs.getTime("due_time") == null ? null : rs.getTime("due_time").toLocalTime().toString());
        m.put("priority", rs.getString("priority"));
        String status = rs.getString("status");
        m.put("status", status);
        m.put("overdue", "PENDIENTE".equals(status) && due.isBefore(Dates.today()));
        m.put("result", rs.getString("result"));
        m.put("origin", rs.getString("origin"));
        m.put("originRef", rs.getString("origin_ref"));
        m.put("createdBy", rs.getString("created_by"));
        m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
        m.put("completedAt", rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
        m.put("completedBy", rs.getString("completed_by"));
        long aid = rs.getLong("assignee_id");
        m.put("assignee", rs.wasNull() ? null : Map.of("id", aid, "fullName", rs.getString("assignee_name")));
        long pid = rs.getLong("patient_id");
        if (!rs.wasNull()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", pid);
            p.put("fullName", rs.getString("patient_name"));
            p.put("phone", showPhone() ? rs.getString("patient_phone") : mask(rs.getString("patient_phone")));
            p.put("hcNumber", rs.getString("hc_number"));
            m.put("patient", p);
        } else m.put("patient", null);
        long lid = rs.getLong("lead_id");
        if (!rs.wasNull()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", lid);
            l.put("fullName", rs.getString("lead_name"));
            l.put("phone", rs.getString("lead_phone"));
            l.put("stage", rs.getString("lead_stage"));
            m.put("lead", l);
        } else m.put("lead", null);
        return m;
    }

    /** Caja ve el teléfono enmascarado en la ficha (minimización de datos); para llamar y cobrar lo necesita completo. */
    private static boolean showPhone() {
        return PermissionService.has("PACIENTES_EDITAR") || PermissionService.has("HISTORIA_CLINICA") || PermissionService.has("CAJA_COBRAR");
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? null : "*****" + phone.substring(phone.length() - 4);
    }

    /** Personal activo que puede tener tareas (para elegir responsable). */
    public List<Map<String, Object>> assignees() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> u : jdbc.queryForList("select id, full_name, role_code, access_role_code from users where active = true order by full_name")) {
            String role = (String) (u.get("access_role_code") != null ? u.get("access_role_code") : u.get("role_code"));
            if (!permissions.forRole(role).contains("TAREAS")) continue;
            out.add(Map.of("id", u.get("id"), "fullName", u.get("full_name"), "role", permissions.label((String) u.get("role_code"))));
        }
        return out;
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public Map<String, Object> create(TaskRequest r) {
        NewTask t = validate(r, true);
        Long id = insert(t, CurrentUser.usernameOrSystem());
        audit.record("TAREA", id, "CREAR", t.title() + (t.patientId() != null ? " · paciente #" + t.patientId() : "") + (t.leadId() != null ? " · lead #" + t.leadId() : ""));
        if (t.leadId() != null) leadEvent(t.leadId(), "TAREA", "Tarea: " + t.title() + " para el " + t.dueDate());
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, TaskRequest r) {
        Map<String, Object> cur = get(id);
        if (!"PENDIENTE".equals(cur.get("status"))) throw ApiException.conflict("TAREA_CERRADA", "La tarea ya está cerrada: reábrela para editarla.");
        NewTask t = validate(new TaskRequest(idOf(cur.get("patient")), idOf(cur.get("lead")), r.kind(), r.title(), r.notes(), r.assigneeId(),
                r.dueDate(), r.dueTime(), r.priority()), false);
        jdbc.update("update tasks set kind = ?, title = ?, notes = ?, assignee_id = ?, due_date = ?, due_time = ?, priority = ?, updated_at = ? where id = ?",
                t.kind(), t.title(), t.notes(), t.assigneeId(), t.dueDate(), t.dueTime(), t.priority(), Dates.now(), id);
        audit.record("TAREA", id, "EDITAR", t.title() + " · para el " + t.dueDate());
        return get(id);
    }

    @Transactional
    public Map<String, Object> complete(Long id, CloseRequest r) { return close(id, "HECHA", r); }

    @Transactional
    public Map<String, Object> cancel(Long id, CloseRequest r) { return close(id, "CANCELADA", r); }

    private Map<String, Object> close(Long id, String status, CloseRequest r) {
        Map<String, Object> cur = get(id);
        if (!"PENDIENTE".equals(cur.get("status"))) throw ApiException.conflict("TAREA_CERRADA", "La tarea ya está cerrada.");
        String result = r == null || r.result() == null ? null : r.result().trim();
        if (result != null && result.length() > 500) throw ApiException.field("result", "Máximo 500 caracteres.");
        if ("CANCELADA".equals(status) && (result == null || result.isBlank())) throw ApiException.field("result", "Indica por qué se cancela.");
        jdbc.update("update tasks set status = ?, result = ?, completed_at = ?, completed_by = ?, updated_at = ? where id = ?",
                status, result == null || result.isBlank() ? null : result, Dates.now(), CurrentUser.usernameOrSystem(), Dates.now(), id);
        audit.record("TAREA", id, "HECHA".equals(status) ? "COMPLETAR" : "CANCELAR", cur.get("title") + (result == null || result.isBlank() ? "" : " · " + result));
        Long lead = idOf(cur.get("lead"));
        if (lead != null) leadEvent(lead, "TAREA", ("HECHA".equals(status) ? "Tarea hecha: " : "Tarea cancelada: ") + cur.get("title")
                + (result == null || result.isBlank() ? "" : " — " + result));
        return get(id);
    }

    @Transactional
    public Map<String, Object> reopen(Long id) {
        Map<String, Object> cur = get(id);
        if ("PENDIENTE".equals(cur.get("status"))) return cur;
        jdbc.update("update tasks set status = 'PENDIENTE', completed_at = null, completed_by = null, updated_at = ? where id = ?", Dates.now(), id);
        audit.record("TAREA", id, "REABRIR", String.valueOf(cur.get("title")));
        return get(id);
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.query(SELECT + " where t.id = ?", (rs, i) -> row(rs), id);
        if (l.isEmpty()) throw ApiException.notFound("La tarea");
        return l.get(0);
    }

    /** Inserta sin validar (quien llama ya validó). Devuelve el id. */
    public Long insert(NewTask t, String by) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        LocalDateTime now = Dates.now();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into tasks (patient_id, lead_id, kind, title, notes, assignee_id, due_date, due_time, priority, status, origin, origin_ref, created_by, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, t.patientId());
            ps.setObject(2, t.leadId());
            ps.setString(3, t.kind());
            ps.setString(4, t.title());
            ps.setString(5, t.notes());
            ps.setObject(6, t.assigneeId());
            ps.setObject(7, t.dueDate());
            ps.setObject(8, t.dueTime());
            ps.setString(9, t.priority());
            ps.setString(10, t.origin());
            ps.setString(11, t.originRef());
            ps.setString(12, by);
            ps.setObject(13, now);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    /** Valida los campos comunes. {@code fresh}: al crear la fecha no puede ser pasada. */
    public NewTask validate(TaskRequest r, boolean fresh) {
        Map<String, String> e = new LinkedHashMap<>();
        String kind = r.kind() == null ? "" : r.kind().trim().toUpperCase();
        if (!KINDS.containsKey(kind)) e.put("kind", "Elige el tipo de tarea.");
        String title = r.title() == null ? "" : r.title().trim().replaceAll("\\s+", " ");
        if (title.length() < 3) e.put("title", "Describe la tarea (mínimo 3 caracteres).");
        else if (title.length() > 150) e.put("title", "Máximo 150 caracteres.");
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        if (notes != null && notes.length() > 1000) e.put("notes", "Máximo 1000 caracteres.");
        if (r.dueDate() == null) e.put("dueDate", "Indica para cuándo es.");
        else if (fresh && r.dueDate().isBefore(Dates.today())) e.put("dueDate", "La fecha no puede ser pasada.");
        else if (r.dueDate().isAfter(Dates.today().plusYears(2))) e.put("dueDate", "Máximo 2 años hacia adelante.");
        LocalTime time = null;
        if (r.dueTime() != null && !r.dueTime().isBlank()) {
            try { time = LocalTime.parse(r.dueTime().trim().length() == 5 ? r.dueTime().trim() : r.dueTime().trim().substring(0, 5)); }
            catch (Exception ex) { e.put("dueTime", "Hora no válida."); }
        }
        String priority = r.priority() == null || r.priority().isBlank() ? "NORMAL" : r.priority().trim().toUpperCase();
        if (!Set.of("NORMAL", "ALTA").contains(priority)) e.put("priority", "Prioridad no válida.");
        if (r.assigneeId() != null && assignees().stream().noneMatch(u -> r.assigneeId().equals(((Number) u.get("id")).longValue())))
            e.put("assigneeId", "El responsable no existe, está inactivo o no puede tener tareas.");
        if (r.patientId() != null && jdbc.queryForObject("select count(*) from patients where id = ? and active = true", Long.class, r.patientId()) == 0)
            e.put("patientId", "El paciente no existe.");
        if (r.leadId() != null && jdbc.queryForObject("select count(*) from leads where id = ?", Long.class, r.leadId()) == 0)
            e.put("leadId", "El lead no existe.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos de la tarea.", e);
        return new NewTask(r.patientId(), r.leadId(), kind, title, notes, r.assigneeId(), r.dueDate(), time, priority, r.leadId() != null ? "LEAD" : "MANUAL", null);
    }

    private void leadEvent(Long leadId, String kind, String detail) {
        jdbc.update("insert into lead_events (lead_id, kind, detail, created_by, created_at) values (?, ?, ?, ?, ?)",
                leadId, kind, detail.length() > 500 ? detail.substring(0, 500) : detail, CurrentUser.usernameOrSystem(), Dates.now());
    }

    @SuppressWarnings("unchecked")
    private static Long idOf(Object o) {
        if (!(o instanceof Map<?, ?> m) || m.get("id") == null) return null;
        return ((Number) ((Map<String, Object>) m).get("id")).longValue();
    }
}
