package pe.clinicacardenas.crm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentEvent;
import pe.clinicacardenas.agenda.AppointmentStatus;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.PatientProfileService;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * v2.7: embudo de ventas. Un lead es alguien interesado (llamó, escribió por Instagram, preguntó un precio) que aún
 * no empieza su tratamiento. Etapas: NUEVO → CONTACTADO → CITA_AGENDADA → ASISTIO → GANADO, o PERDIDO.
 * Avanza solo cuando el paciente vinculado agenda su cita o es atendido.
 */
@Service
public class LeadService {
    private static final String ASISTIO_2 = "ASISTIO";
    private static final String CITA_AGENDADA_2 = "CITA_AGENDADA";
    private static final String CONTACTADO_2 = "CONTACTADO";
    private static final String GANADO_2 = "GANADO";
    private static final String NUEVO_2 = "NUEVO";
    private static final String PERDIDO_2 = "PERDIDO";
    private static final String CLOSED_AT = "closed_at";
    private static final String EMAIL = "email";
    private static final String ESTIMATEDVALUE = "estimatedValue";
    private static final String FULLNAME = "fullName";
    private static final String INTEREST = "interest";
    private static final String LABEL = "label";
    private static final String LOSTREASON = "lostReason";
    private static final String LOST_REASON = "lost_reason";
    private static final String NOTES = "notes";
    private static final String PATIENT = "patient";
    private static final String PHONE = "phone";
    private static final String SISTEMA = "sistema";
    private static final String SOURCE = "source";
    private static final String STAGE = "stage";


    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private static final Map<String, String> STAGES_EDIT = new LinkedHashMap<>();
    public static final Map<String, String> STAGES = java.util.Collections.unmodifiableMap(STAGES_EDIT);
    static {
        STAGES_EDIT.put(NUEVO_2, "Nuevo");
        STAGES_EDIT.put(CONTACTADO_2, "Contactado");
        STAGES_EDIT.put(CITA_AGENDADA_2, "Cita agendada");
        STAGES_EDIT.put(ASISTIO_2, "Asistió a evaluación");
        STAGES_EDIT.put(GANADO_2, "Aceptó tratamiento");
        STAGES_EDIT.put(PERDIDO_2, "Perdido");
    }
    public static final List<String> OPEN = List.of(NUEVO_2, CONTACTADO_2, CITA_AGENDADA_2, ASISTIO_2);
    public static final List<String> LOST_REASONS = List.of("Precio", "No responde", "Eligió otra clínica", "Ya no le interesa",
            "Distancia u horario", "Otro");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TaskService tasks;

    public LeadService(JdbcTemplate jdbc, AuditService audit, TaskService tasks) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.tasks = tasks;
    }

    public record LeadRequest(String fullName, String phone, String email, String source, String interest, Long procedureId,
                              BigDecimal estimatedValue, Long assigneeId, String notes, LocalDate followUpDate) {}

    public record StageRequest(String stage, String lostReason) {}

    public record NoteRequest(String text) {}

    public record LinkRequest(Long patientId) {}

    // ------------------------------------------------------------------ consultas

    private static final String SELECT = """
            select l.*, u.full_name as assignee_name, concat(p.first_name, ' ', p.last_name) as patient_name, pr.name as procedure_name,
                   (select min(a.start_at) from appointments a where a.patient_id = l.patient_id and a.start_at > ?
                       and a.status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION')) as next_appt
              from leads l
              left join users u on u.id = l.assignee_id
              left join patients p on p.id = l.patient_id
              left join procedures pr on pr.id = l.procedure_id
            """;

    @Transactional(readOnly = true)
    public Map<String, Object> board(String q, String source, Long assigneeId, int closedDays) {
        LocalDateTime now = Dates.now();
        List<String> c = new ArrayList<>();
        List<Object> a = new ArrayList<>();
        a.add(now);
        c.add("(l.stage in ('NUEVO', 'CONTACTADO', 'CITA_AGENDADA', 'ASISTIO') or l.closed_at >= ?)");
        a.add(now.minusDays(Math.clamp(closedDays, 1, 365)));
        if (q != null && !q.isBlank()) {
            c.add("(lower(l.full_name) like ? or l.phone like ? or lower(coalesce(l.interest, '')) like ?)");
            String like = "%" + q.trim().toLowerCase() + "%";
            a.add(like); a.add("%" + q.trim() + "%"); a.add(like);
        }
        if (source != null && !source.isBlank()) { c.add("l.source = ?"); a.add(source.trim().toUpperCase()); }
        if (assigneeId != null) { c.add("l.assignee_id = ?"); a.add(assigneeId); }
        List<Map<String, Object>> cards = jdbc.query(SELECT + " where " + String.join(" and ", c) + " order by l.stage_changed_at desc limit 600",
                (rs, i) -> card(rs), a.toArray());
        Map<Long, Map<String, Object>> nextTasks = nextTasks(cards.stream().map(x -> (Long) x.get("id")).toList());
        List<Map<String, Object>> stages = new ArrayList<>();
        for (var st : STAGES.entrySet()) {
            List<Map<String, Object>> items = new ArrayList<>();
            BigDecimal value = BigDecimal.ZERO;
            for (Map<String, Object> card : cards) {
                if (!st.getKey().equals(card.get(STAGE))) continue;
                card.put("nextTask", nextTasks.get(card.get("id")));
                items.add(card);
                if (card.get(ESTIMATEDVALUE) instanceof BigDecimal b) value = value.add(b);
            }
            stages.add(Map.of("code", st.getKey(), LABEL, st.getValue(), "count", items.size(), "value", value, "items", items));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stages", stages);
        out.put("options", options());
        return out;
    }

    public Map<String, Object> options() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("stages", STAGES.entrySet().stream().map(e -> Map.of("code", e.getKey(), LABEL, e.getValue())).toList());
        o.put("sources", PatientProfileService.SOURCES);
        o.put("lostReasons", LOST_REASONS);
        o.put("assignees", tasks.assignees());
        o.put("procedures", jdbc.queryForList("select id, name, price from procedures where active = true order by name"));
        o.put("whatsappAuto", "true".equals(setting("leads_whatsapp_auto")));
        return o;
    }

    private Map<Long, Map<String, Object>> nextTasks(List<Long> ids) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("select lead_id, id, title, due_date from tasks where status = 'PENDIENTE' and lead_id in ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ") order by due_date, id", rs -> {
            long lid = rs.getLong(1);
            if (out.containsKey(lid)) return;
            LocalDate due = rs.getDate(4).toLocalDate();
            out.put(lid, Map.of("id", rs.getLong(2), "title", rs.getString(3), "dueDate", due, "overdue", due.isBefore(Dates.today())));
        }, ids.toArray());
        return out;
    }

    private Map<String, Object> card(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put(FULLNAME, rs.getString("full_name"));
        m.put(PHONE, rs.getString(PHONE));
        m.put(EMAIL, rs.getString(EMAIL));
        String src = rs.getString(SOURCE);
        m.put(SOURCE, src);
        m.put("sourceLabel", PatientProfileService.SOURCES.stream().filter(x -> x.get("code").equals(src)).map(x -> x.get(LABEL)).findFirst().orElse(src));
        m.put(INTEREST, rs.getString(INTEREST));
        long proc = rs.getLong("procedure_id");
        m.put("procedure", rs.wasNull() ? null : Map.of("id", proc, "name", rs.getString("procedure_name")));
        m.put(ESTIMATEDVALUE, rs.getBigDecimal("estimated_value"));
        String stage = rs.getString(STAGE);
        m.put(STAGE, stage);
        m.put("stageLabel", STAGES.get(stage));
        long aid = rs.getLong("assignee_id");
        m.put("assignee", rs.wasNull() ? null : Map.of("id", aid, FULLNAME, rs.getString("assignee_name")));
        long pid = rs.getLong("patient_id");
        m.put(PATIENT, rs.wasNull() ? null : Map.of("id", pid, FULLNAME, rs.getString("patient_name")));
        m.put("nextAppointment", rs.getTimestamp("next_appt") == null ? null : rs.getTimestamp("next_appt").toLocalDateTime());
        m.put(LOSTREASON, rs.getString(LOST_REASON));
        m.put(NOTES, rs.getString(NOTES));
        LocalDateTime changed = rs.getTimestamp("stage_changed_at").toLocalDateTime();
        m.put("stageChangedAt", changed);
        m.put("daysInStage", ChronoUnit.DAYS.between(changed.toLocalDate(), Dates.today()));
        m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
        m.put("createdBy", rs.getString("created_by"));
        m.put("closedAt", rs.getTimestamp(CLOSED_AT) == null ? null : rs.getTimestamp(CLOSED_AT).toLocalDateTime());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.query(SELECT + " where l.id = ?", (rs, i) -> card(rs), Dates.now(), id);
        if (l.isEmpty()) throw ApiException.notFound("El lead");
        Map<String, Object> m = l.get(0);
        if (m.get(PATIENT) == null && m.get(PHONE) != null)
            m.put("phoneMatches", jdbc.queryForList("select id, concat(first_name, ' ', last_name) as fullName, doc_number as docNumber from patients "
                    + "where active = true and replace(replace(phone, ' ', ''), '-', '') = ? order by last_name limit 5", m.get(PHONE)));
        m.put("events", jdbc.queryForList("select kind, detail, created_by as createdBy, created_at as createdAt from lead_events where lead_id = ? order by created_at desc, id desc", id));
        m.put("tasks", tasks.list("all", "TODAS", null, null, null, id, null).get("items"));
        return m;
    }

    /** Métricas del embudo para los leads creados en los últimos {@code days} días. */
    @Transactional(readOnly = true)
    public Map<String, Object> metrics(int days) {
        int d = Math.clamp(days, 7, 365);
        LocalDateTime since = Dates.today().minusDays(d).atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList("select stage, lost_from_stage, source, lost_reason, estimated_value, created_at, closed_at from leads where created_at >= ?", since);
        List<String> order = List.of(NUEVO_2, CONTACTADO_2, CITA_AGENDADA_2, ASISTIO_2, GANADO_2);
        long[] reached = new long[order.size()];
        long won = 0;
        long lost = 0;
        long open = 0;
        BigDecimal pipeline = BigDecimal.ZERO;
        BigDecimal wonValue = BigDecimal.ZERO;
        long winDays = 0;
        Map<String, long[]> bySource = new LinkedHashMap<>();
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String st = (String) r.get(STAGE);
            String lostFrom = r.get("lost_from_stage") == null ? NUEVO_2 : (String) r.get("lost_from_stage");
            String eff = PERDIDO_2.equals(st) ? lostFrom : st;
            int idx = Math.max(0, order.indexOf(eff));
            for (int i = 0; i <= idx; i++) reached[i]++;
            BigDecimal v = r.get("estimated_value") instanceof BigDecimal b ? b : BigDecimal.ZERO;
            long[] s = bySource.computeIfAbsent((String) r.get(SOURCE), k -> new long[2]);
            s[0]++;
            if (GANADO_2.equals(st)) {
                won++; s[1]++; wonValue = wonValue.add(v);
                LocalDateTime cl = Dates.toLocalDateTime(r.get(CLOSED_AT));
                LocalDateTime cr = Dates.toLocalDateTime(r.get("created_at"));
                if (cl != null && cr != null) winDays += ChronoUnit.DAYS.between(cr, cl);
            } else if (PERDIDO_2.equals(st)) {
                lost++;
                String reason = r.get(LOST_REASON) == null ? "Otro" : ((String) r.get(LOST_REASON)).split(":")[0].trim();
                reasons.merge(reason, 1L, Long::sum);
            } else { open++; pipeline = pipeline.add(v); }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", d);
        m.put("created", rows.size());
        m.put("open", open);
        m.put("won", won);
        m.put("lost", lost);
        m.put("conversion", rows.isEmpty() ? 0 : BigDecimal.valueOf(won * 100.0 / rows.size()).setScale(1, RoundingMode.HALF_UP));
        m.put("pipelineValue", pipeline);
        m.put("wonValue", wonValue);
        m.put("avgDaysToWin", won == 0 ? null : BigDecimal.valueOf(winDays / (double) won).setScale(1, RoundingMode.HALF_UP));
        List<Map<String, Object>> funnel = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            funnel.add(Map.of(STAGE, order.get(i), LABEL, STAGES.get(order.get(i)), "reached", reached[i],
                    "percent", rows.isEmpty() ? 0 : Math.round(reached[i] * 100.0 / rows.size())));
        }
        m.put("funnel", funnel);
        List<Map<String, Object>> src = new ArrayList<>();
        bySource.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).forEach(e -> src.add(Map.of(
                SOURCE, e.getKey(),
                LABEL, PatientProfileService.SOURCES.stream().filter(x -> x.get("code").equals(e.getKey())).map(x -> x.get(LABEL)).findFirst().orElse(e.getKey()),
                "total", e.getValue()[0], "won", e.getValue()[1],
                "conversion", Math.round(e.getValue()[1] * 1000.0 / e.getValue()[0]) / 10.0)));
        m.put("bySource", src);
        m.put("lostReasons", reasons.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                .map(e -> Map.of("reason", e.getKey(), "count", e.getValue())).toList());
        return m;
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public Map<String, Object> create(LeadRequest r) {
        Clean c = validate(r, null);
        LocalDateTime now = Dates.now();
        String by = CurrentUser.usernameOrSystem();
        // No se vincula solo por el celular (una familia puede compartirlo): la ficha sugiere los pacientes con ese número.
        Long id = insert(c, null, by, now);
        event(id, "CREADO", "Lead registrado · " + sourceLabel(c.source) + (c.interest == null ? "" : " · " + c.interest), by);
        if (r.followUpDate() != null) {
            var t = tasks.validate(new TaskService.TaskRequest(null, id, "LLAMAR", "Contactar a " + c.name, null, c.assigneeId, r.followUpDate(), null, "NORMAL"), true);
            tasks.insert(t, by);
            event(id, "TAREA", "Tarea: " + t.title() + " para el " + t.dueDate(), by);
        }
        audit.register("LEAD", id, "CREAR", c.name + " · " + c.source);
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, LeadRequest r) {
        get(id);
        Clean c = validate(r, id);
        jdbc.update("""
                update leads set full_name = ?, phone = ?, email = ?, source = ?, interest = ?, procedure_id = ?, estimated_value = ?,
                       assignee_id = ?, notes = ?, updated_at = ? where id = ?
                """, c.name, c.phone, c.email, c.source, c.interest, c.procedureId, c.value, c.assigneeId, c.notes, Dates.now(), id);
        audit.register("LEAD", id, "EDITAR", c.name);
        return get(id);
    }

    @Transactional
    public Map<String, Object> stage(Long id, StageRequest r) {
        Map<String, Object> l = get(id);
        String st = r.stage() == null ? "" : r.stage().trim().toUpperCase();
        if (!STAGES.containsKey(st)) throw ApiException.field(STAGE, "Etapa no válida.");
        if (st.equals(l.get(STAGE))) return l;
        String reason = null;
        if (PERDIDO_2.equals(st)) {
            reason = r.lostReason() == null ? "" : r.lostReason().trim();
            if (reason.isBlank()) throw ApiException.field(LOSTREASON, "Indica por qué se perdió.");
            if (reason.length() > 200) throw ApiException.field(LOSTREASON, "Máximo 200 caracteres.");
        }
        if (GANADO_2.equals(st) && l.get(PATIENT) == null)
            throw ApiException.badRequest("SIN_PACIENTE", "Primero registra o vincula al paciente: así el tratamiento queda en su ficha.");
        move(id, st, reason, null, CurrentUser.usernameOrSystem());
        return get(id);
    }

    @Transactional
    public Map<String, Object> note(Long id, NoteRequest r) {
        get(id);
        String t = r.text() == null ? "" : r.text().trim();
        if (t.length() < 2 || t.length() > 500) throw ApiException.field("text", "Escribe una nota de 2 a 500 caracteres.");
        event(id, "NOTA", t, CurrentUser.usernameOrSystem());
        jdbc.update("update leads set updated_at = ? where id = ?", Dates.now(), id);
        return get(id);
    }

    @Transactional
    public Map<String, Object> linkPatient(Long id, LinkRequest r) {
        Map<String, Object> l = get(id);
        if (r.patientId() == null || jdbc.queryForObject("select count(*) from patients where id = ? and active = true", Long.class, r.patientId()) == 0)
            throw ApiException.field("patientId", "El paciente no existe.");
        jdbc.update("update leads set patient_id = ?, updated_at = ? where id = ?", r.patientId(), Dates.now(), id);
        String by = CurrentUser.usernameOrSystem();
        event(id, "PACIENTE", "Vinculado al paciente " + patientName(r.patientId()), by);
        if (List.of(NUEVO_2, CONTACTADO_2).contains(l.get(STAGE)) && hasFutureAppointment(r.patientId()))
            move(id, CITA_AGENDADA_2, null, "Ya tiene una cita agendada", by);
        audit.register("LEAD", id, "VINCULAR", "Paciente #" + r.patientId());
        return get(id);
    }

    @Transactional
    public Map<String, Object> setWhatsappAuto(boolean on) {
        if (jdbc.update("update app_settings set v = ?, updated_at = ?, updated_by = ? where k = 'leads_whatsapp_auto'", String.valueOf(on), Dates.now(), CurrentUser.usernameOrSystem()) == 0)
            jdbc.update("insert into app_settings (k, v, updated_at, updated_by) values ('leads_whatsapp_auto', ?, ?, ?)", String.valueOf(on), Dates.now(), CurrentUser.usernameOrSystem());
        audit.register("LEAD", null, "WHATSAPP_AUTO", on ? "Activado" : "Desactivado");
        return options();
    }

    // ------------------------------------------------------------------ automático

    /** La agenda mueve el lead: al agendar la cita → Cita agendada; al ser atendido → Asistió; si cancela → Contactado. */
    @EventListener
    public void onAppointment(AppointmentEvent e) {
        Appointment a = e.appointment();
        if (a == null || a.patient == null) return;
        try {
            List<Map<String, Object>> open = jdbc.queryForList("select id, stage, appointment_id from leads where patient_id = ? and stage in ('NUEVO', 'CONTACTADO', 'CITA_AGENDADA', 'ASISTIO')", a.patient.id);
            for (Map<String, Object> l : open) {
                Long id = ((Number) l.get("id")).longValue();
                String st = (String) l.get(STAGE);
                if (e.type() == AppointmentEvent.Type.CREATED && List.of(NUEVO_2, CONTACTADO_2).contains(st)) {
                    jdbc.update("update leads set appointment_id = ? where id = ?", a.id, id);
                    move(id, CITA_AGENDADA_2, null, "Cita agendada para el " + a.startAt.toLocalDate() + " " + a.startAt.toLocalTime(), SISTEMA);
                } else if (e.type() == AppointmentEvent.Type.STATUS && a.status == AppointmentStatus.ATENDIDA && !ASISTIO_2.equals(st)) {
                    move(id, ASISTIO_2, null, "Fue atendido el " + a.startAt.toLocalDate(), SISTEMA);
                } else if (e.type() == AppointmentEvent.Type.STATUS && a.status == AppointmentStatus.CANCELADA && CITA_AGENDADA_2.equals(st)
                        && a.id.equals(l.get("appointment_id") == null ? null : ((Number) l.get("appointment_id")).longValue()) && !hasFutureAppointment(a.patient.id)) {
                    move(id, CONTACTADO_2, null, "Canceló su cita", SISTEMA);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Embudo: no se pudo actualizar el lead del paciente {}: {}", a.patient.id, ex.getMessage());
        }
    }

    /** Quien escribe por WhatsApp sin ser paciente queda como lead nuevo (si está activado y no existe ya). */
    @Transactional
    public void fromWhatsApp(String phone, String text) {
        try {
            if (!"true".equals(setting("leads_whatsapp_auto"))) return;
            String p = phone == null ? "" : phone.replaceAll("\\D", "");
            if (p.startsWith("51") && p.length() == 11) p = p.substring(2);
            if (!p.matches("9\\d{8}")) return;
            if (jdbc.queryForObject("select count(*) from leads where phone = ? and (stage in ('NUEVO', 'CONTACTADO', 'CITA_AGENDADA', 'ASISTIO') or created_at >= ?)",
                    Long.class, p, Dates.now().minusDays(30)) > 0) return;
            if (matchPatient(p) != null) return;
            String interest = text == null ? null : text.trim().replaceAll("\\s+", " ");
            if (interest != null && interest.length() > 150) interest = interest.substring(0, 147) + "…";
            Clean c = new Clean("Contacto WhatsApp " + p.substring(0, 3) + " " + p.substring(3, 6) + " " + p.substring(6), p, null, "WHATSAPP", interest, null, null, null, null);
            Long id = insert(c, null, SISTEMA, Dates.now());
            event(id, "CREADO", "Escribió por WhatsApp" + (interest == null ? "" : ": “" + interest + "”"), SISTEMA);
        } catch (RuntimeException ex) {
            log.warn("No se pudo crear el lead de WhatsApp: {}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ utilidades

    private void move(Long id, String to, String lostReason, String why, String by) {
        String from = jdbc.queryForObject("select stage from leads where id = ?", String.class, id);
        boolean closing = GANADO_2.equals(to) || PERDIDO_2.equals(to);
        jdbc.update("update leads set stage = ?, stage_changed_at = ?, lost_reason = ?, lost_from_stage = ?, closed_at = ?, updated_at = ? where id = ?",
                to, Dates.now(), PERDIDO_2.equals(to) ? lostReason : null, PERDIDO_2.equals(to) ? from : null, closing ? Dates.now() : null, Dates.now(), id);
        event(id, "ETAPA", STAGES.get(from) + " → " + STAGES.get(to) + (lostReason != null ? " (" + lostReason + ")" : "") + (why != null ? " · " + why : ""), by);
        audit.register("LEAD", id, "ETAPA", from + " → " + to);
    }

    private void event(Long id, String kind, String detail, String by) {
        jdbc.update("insert into lead_events (lead_id, kind, detail, created_by, created_at) values (?, ?, ?, ?, ?)",
                id, kind, detail.length() > 500 ? detail.substring(0, 500) : detail, by, Dates.now());
    }

    private record Clean(String name, String phone, String email, String source, String interest, Long procedureId, BigDecimal value,
                         Long assigneeId, String notes) {}

    private Long insert(Clean c, Long patientId, String by, LocalDateTime now) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into leads (full_name, phone, email, source, interest, procedure_id, estimated_value, stage, assignee_id, patient_id,
                                       notes, stage_changed_at, created_by, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'NUEVO', ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, c.name);
            ps.setString(2, c.phone);
            ps.setString(3, c.email);
            ps.setString(4, c.source);
            ps.setString(5, c.interest);
            ps.setObject(6, c.procedureId);
            ps.setBigDecimal(7, c.value);
            ps.setObject(8, c.assigneeId);
            ps.setObject(9, patientId);
            ps.setString(10, c.notes);
            ps.setObject(11, now);
            ps.setString(12, by);
            ps.setObject(13, now);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private Clean validate(LeadRequest r, Long id) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.fullName() == null ? "" : r.fullName().trim().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 120) e.put(FULLNAME, "Escribe el nombre (3 a 120 caracteres).");
        String phone = r.phone() == null ? "" : r.phone().replaceAll("[\\s-]", "");
        if (phone.startsWith("+51")) phone = phone.substring(3);
        if (!phone.isEmpty() && !phone.matches("9\\d{8}")) e.put(PHONE, "Celular de 9 dígitos que empiece con 9.");
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!email.isEmpty() && !pe.clinicacardenas.common.Emails.valid(email)) e.put(EMAIL, "Correo no válido.");
        if (phone.isEmpty() && email.isEmpty()) e.put(PHONE, "Indica un celular o un correo para poder contactarlo.");
        String source = r.source() == null ? "" : r.source().trim().toUpperCase();
        if (PatientProfileService.SOURCES.stream().noneMatch(x -> x.get("code").equals(source))) e.put(SOURCE, "Elige de dónde llegó.");
        String interest = r.interest() == null || r.interest().isBlank() ? null : r.interest().trim();
        if (interest != null && interest.length() > 150) e.put(INTEREST, "Máximo 150 caracteres.");
        if (r.procedureId() != null && jdbc.queryForObject("select count(*) from procedures where id = ?", Long.class, r.procedureId()) == 0)
            e.put("procedureId", "Procedimiento no válido.");
        BigDecimal v = r.estimatedValue();
        if (v != null && (v.signum() < 0 || v.compareTo(new BigDecimal("100000")) > 0)) e.put(ESTIMATEDVALUE, "Entre S/ 0 y S/ 100 000.");
        if (r.assigneeId() != null && tasks.assignees().stream().noneMatch(u -> r.assigneeId().equals(((Number) u.get("id")).longValue())))
            e.put("assigneeId", "Responsable no válido.");
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        if (notes != null && notes.length() > 1000) e.put(NOTES, "Máximo 1000 caracteres.");
        if (r.followUpDate() != null && r.followUpDate().isBefore(Dates.today())) e.put("followUpDate", "La fecha no puede ser pasada.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del lead.", e);
        if (!phone.isEmpty()) {
            List<Long> dup = jdbc.queryForList("select id from leads where phone = ? and stage in ('NUEVO', 'CONTACTADO', 'CITA_AGENDADA', 'ASISTIO') and id <> ?",
                    Long.class, phone, id == null ? -1L : id);
            if (!dup.isEmpty()) throw ApiException.conflict("LEAD_DUPLICADO", "Ya hay un lead abierto con ese celular (#" + dup.get(0) + ").",
                    Map.of(PHONE, "Ya hay un lead abierto con ese celular.", "leadId", String.valueOf(dup.get(0))));
        }
        BigDecimal value = v;
        if (value == null && r.procedureId() != null)
            value = jdbc.queryForObject("select price from procedures where id = ?", BigDecimal.class, r.procedureId());
        return new Clean(name, phone.isEmpty() ? null : phone, email.isEmpty() ? null : email, source, interest, r.procedureId(), value, r.assigneeId(), notes);
    }

    private Long matchPatient(String phone) {
        if (phone == null) return null;
        List<Long> ids = jdbc.queryForList("select id from patients where active = true and replace(replace(phone, ' ', ''), '-', '') = ?", Long.class, phone);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private boolean hasFutureAppointment(Long patientId) {
        return jdbc.queryForObject("select count(*) from appointments where patient_id = ? and start_at > ? and status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION')",
                Long.class, patientId, Dates.now()) > 0;
    }

    private String patientName(Long id) {
        return jdbc.queryForObject("select concat(first_name, ' ', last_name) from patients where id = ?", String.class, id);
    }

    private static String sourceLabel(String code) {
        return PatientProfileService.SOURCES.stream().filter(x -> x.get("code").equals(code)).map(x -> x.get(LABEL)).findFirst().orElse(code);
    }

    private String setting(String k) {
        List<String> l = jdbc.queryForList("select v from app_settings where k = ?", String.class, k);
        return l.isEmpty() ? null : l.get(0);
    }
}
