package pe.clinicacardenas.crm;

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
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.*;

/**
 * v2.7: campañas a un segmento. Solo llegan a quienes aceptaron publicidad (Ley 29733; art. 58 del Código de
 * Protección al Consumidor), cada mensaje dice cómo darse de baja y el resultado se mide en citas reservadas.
 * Estados: BORRADOR → PROGRAMADA → ENVIADA, o CANCELADA.
 */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    public static final int MAX_RECIPIENTS = 2000;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final MarketingService marketing;
    private final PatientRepository patients;
    private final NotificationService notifications;

    public CampaignService(JdbcTemplate jdbc, AuditService audit, MarketingService marketing, PatientRepository patients, NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.marketing = marketing;
        this.patients = patients;
        this.notifications = notifications;
    }

    public record CampaignRequest(String name, Long segmentId, SegmentFilter filters, String channel, String subject, String body) {}

    public record ScheduleRequest(LocalDateTime scheduledAt) {}

    public record PreviewRequest(String subject, String body) {}

    // ------------------------------------------------------------------ consultas

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select id from campaigns order by coalesce(sent_at, scheduled_at, created_at) desc limit 200"))
            out.add(get(((Number) r.get("id")).longValue()));
        return out;
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.*, s.name as segment_name from campaigns c left join segments s on s.id = c.segment_id where c.id = ?
                """, id);
        if (rows.isEmpty()) throw ApiException.notFound("La campaña");
        Map<String, Object> r = rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("name", r.get("name"));
        m.put("segmentId", r.get("segment_id"));
        m.put("segmentName", r.get("segment_name"));
        SegmentFilter f = marketing.parse(r.get("filters"));
        m.put("filters", f);
        m.put("audience", f.describe());
        m.put("channel", r.get("channel"));
        m.put("subject", r.get("subject"));
        m.put("body", r.get("body"));
        m.put("status", r.get("status"));
        m.put("scheduledAt", r.get("scheduled_at"));
        m.put("sentAt", r.get("sent_at"));
        m.put("recipients", r.get("recipients"));
        m.put("createdBy", r.get("created_by"));
        m.put("createdAt", r.get("created_at"));
        if ("ENVIADA".equals(r.get("status"))) m.put("stats", stats(id, Dates.toLocalDateTime(r.get("sent_at"))));
        else {
            List<Long> ids = marketing.ids(f);
            m.put("estimated", Math.min(MAX_RECIPIENTS, marketing.reachable(ids).size()));
            m.put("segmentCount", ids.size());
        }
        return m;
    }

    /** Entregados, leídos y fallidos; bajas después del envío; y pacientes que reservaron una cita en los 30 días siguientes. */
    private Map<String, Object> stats(Long id, LocalDateTime sentAt) {
        Map<String, Object> s = new LinkedHashMap<>();
        Map<String, Long> by = new LinkedHashMap<>();
        jdbc.query("""
                select n.status, count(*) from campaign_recipients r join notifications n on n.id = r.notification_id
                 where r.campaign_id = ? group by n.status
                """, rs -> { by.put(rs.getString(1), rs.getLong(2)); }, id);
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        long delivered = by.getOrDefault("ENTREGADO", 0L) + by.getOrDefault("LEIDO", 0L);
        s.put("messages", total);
        s.put("delivered", delivered + by.getOrDefault("ENVIADO", 0L));
        s.put("read", by.getOrDefault("LEIDO", 0L));
        s.put("failed", by.getOrDefault("FALLIDO", 0L));
        s.put("pending", by.getOrDefault("PENDIENTE", 0L));
        if (sentAt != null) {
            s.put("optOuts", jdbc.queryForObject("""
                    select count(distinct r.patient_id) from campaign_recipients r join patients p on p.id = r.patient_id
                     where r.campaign_id = ? and p.marketing_consent = false and p.marketing_consent_at >= ?
                    """, Long.class, id, sentAt));
            s.put("booked", jdbc.queryForObject("""
                    select count(distinct r.patient_id) from campaign_recipients r join appointments a on a.patient_id = r.patient_id
                     where r.campaign_id = ? and a.created_at >= ? and a.created_at < ? and a.status <> 'CANCELADA'
                    """, Long.class, id, sentAt, sentAt.plusDays(30)));
        }
        return s;
    }

    public Map<String, Object> preview(PreviewRequest r) {
        Map<String, String> v = new LinkedHashMap<>();
        for (String k : NotificationService.PATIENT_VARS) v.put(k, MessageTemplateService.VARIABLES.get(k));
        return Map.of("subject", MessageTemplateService.fill(r.subject(), v), "body", MessageTemplateService.fill(r.body(), v),
                "footer", "Responde *BAJA* si no deseas recibir más promociones.", "variables", NotificationService.PATIENT_VARS);
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public Map<String, Object> create(CampaignRequest r) {
        Clean c = validate(r);
        String by = CurrentUser.usernameOrSystem();
        jdbc.update("""
                insert into campaigns (name, segment_id, filters, channel, subject, body, status, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, 'BORRADOR', ?, ?)
                """, c.name, r.segmentId(), marketing.write(c.filters), c.channel, c.subject, c.body, by, Dates.now());
        Long id = jdbc.queryForObject("select max(id) from campaigns where created_by = ?", Long.class, by);
        audit.record("CAMPANA", id, "CREAR", c.name + " · " + c.filters.describe());
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, CampaignRequest r) {
        String st = status(id);
        if (!Set.of("BORRADOR", "PROGRAMADA").contains(st)) throw ApiException.conflict("CAMPANA_CERRADA", "La campaña ya se envió o se canceló.");
        Clean c = validate(r);
        jdbc.update("update campaigns set name = ?, segment_id = ?, filters = ?, channel = ?, subject = ?, body = ?, updated_by = ?, updated_at = ? where id = ?",
                c.name, r.segmentId(), marketing.write(c.filters), c.channel, c.subject, c.body, CurrentUser.usernameOrSystem(), Dates.now(), id);
        audit.record("CAMPANA", id, "EDITAR", c.name);
        return get(id);
    }

    /** Programa la campaña; sin fecha (o con una ya pasada) se envía ahora mismo. */
    @Transactional
    public Map<String, Object> schedule(Long id, ScheduleRequest r) {
        String st = status(id);
        if (!Set.of("BORRADOR", "PROGRAMADA").contains(st)) throw ApiException.conflict("CAMPANA_CERRADA", "La campaña ya se envió o se canceló.");
        LocalDateTime at = r == null ? null : r.scheduledAt();
        LocalDateTime now = Dates.now();
        if (at != null && at.isAfter(now.plusDays(90))) throw ApiException.field("scheduledAt", "Máximo 90 días hacia adelante.");
        if (at != null && at.isAfter(now.plusMinutes(1))) {
            int h = at.getHour();
            if (h < 8 || h >= 20) throw ApiException.field("scheduledAt", "Programa el envío entre las 08:00 y las 20:00.");
            jdbc.update("update campaigns set status = 'PROGRAMADA', scheduled_at = ?, updated_by = ?, updated_at = ? where id = ?", at, CurrentUser.usernameOrSystem(), now, id);
            audit.record("CAMPANA", id, "PROGRAMAR", "Para el " + at);
            return get(id);
        }
        int h = now.getHour();
        if (h < 8 || h >= 20) throw ApiException.badRequest("FUERA_DE_HORARIO", "Las campañas se envían entre las 08:00 y las 20:00. Prográmala para mañana.");
        jdbc.update("update campaigns set status = 'PROGRAMADA', scheduled_at = ? where id = ?", now, id);
        send(id);
        return get(id);
    }

    @Transactional
    public Map<String, Object> cancel(Long id) {
        String st = status(id);
        if (!Set.of("BORRADOR", "PROGRAMADA").contains(st)) throw ApiException.conflict("CAMPANA_CERRADA", "La campaña ya se envió o se canceló.");
        jdbc.update("update campaigns set status = 'CANCELADA', updated_by = ?, updated_at = ? where id = ?", CurrentUser.usernameOrSystem(), Dates.now(), id);
        audit.record("CAMPANA", id, "CANCELAR", "");
        return get(id);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void tick() {
        for (Long id : jdbc.queryForList("select id from campaigns where status = 'PROGRAMADA' and scheduled_at <= ?", Long.class, Dates.now())) {
            try { send(id); } catch (Exception e) { log.warn("Campaña {}: {}", id, e.getMessage()); }
        }
    }

    /** Envía una campaña PROGRAMADA. El cambio de estado condicionado evita que dos procesos la envíen a la vez. */
    @Transactional
    public synchronized int send(Long id) {
        if (jdbc.update("update campaigns set status = 'ENVIANDO' where id = ? and status = 'PROGRAMADA'", id) == 0) return 0;
        Map<String, Object> c = jdbc.queryForMap("select name, filters, channel, subject, body from campaigns where id = ?", id);
        SegmentFilter f = marketing.parse(c.get("filters"));
        List<Long> ids = marketing.reachable(marketing.ids(f));
        if (ids.size() > MAX_RECIPIENTS) ids = ids.subList(0, MAX_RECIPIENTS);
        int people = 0;
        for (Long pid : ids) {
            Patient p = patients.findById(pid).orElse(null);
            if (p == null || !Boolean.TRUE.equals(p.marketingConsent)) continue;
            var sent = notifications.marketing(p, "CAMPANA", null, (String) c.get("subject"), (String) c.get("body"), (String) c.get("channel"), "campana:" + id + ":" + pid);
            for (var s : sent) {
                jdbc.update("insert into campaign_recipients (campaign_id, patient_id, channel, notification_id, created_at) values (?, ?, ?, ?, ?)",
                        id, pid, s.channel(), s.notificationId(), Dates.now());
            }
            if (!sent.isEmpty()) people++;
        }
        jdbc.update("update campaigns set status = 'ENVIADA', sent_at = ?, recipients = ? where id = ?", Dates.now(), people, id);
        audit.record("CAMPANA", id, "ENVIAR", c.get("name") + " · " + people + " pacientes");
        log.info("Campaña {} enviada a {} pacientes", id, people);
        return people;
    }

    // ------------------------------------------------------------------ validación

    private record Clean(String name, SegmentFilter filters, String channel, String subject, String body) {}

    private Clean validate(CampaignRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.name() == null ? "" : r.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 100) e.put("name", "Ponle un nombre de 3 a 100 caracteres.");
        String channel = r.channel() == null || r.channel().isBlank() ? "PREFERIDO" : r.channel().trim().toUpperCase();
        if (!Set.of("PREFERIDO", "WHATSAPP", "EMAIL").contains(channel)) e.put("channel", "Canal no válido.");
        String subject = r.subject() == null ? "" : r.subject().trim();
        if (subject.length() < 3 || subject.length() > 150) e.put("subject", "El asunto debe tener de 3 a 150 caracteres.");
        String body = r.body() == null ? "" : r.body().trim();
        if (body.length() < 10 || body.length() > 1000) e.put("body", "El mensaje debe tener de 10 a 1000 caracteres.");
        for (String[] fld : new String[][]{{"subject", subject}, {"body", body}}) {
            List<String> bad = new ArrayList<>(MessageTemplateService.unknownVars(fld[1]));
            var m = java.util.regex.Pattern.compile("\\{([a-z_]+)\\}").matcher(fld[1]);
            while (m.find()) if (!NotificationService.PATIENT_VARS.contains(m.group(1)) && !bad.contains(m.group(1))) bad.add(m.group(1));
            if (!bad.isEmpty()) e.put(fld[0], "Variables que no se pueden usar en campañas: {" + String.join("}, {", bad) + "}. Usa {"
                    + String.join("}, {", NotificationService.PATIENT_VARS) + "}.");
        }
        SegmentFilter f;
        if (r.segmentId() != null) f = marketing.segmentFilter(r.segmentId());
        else f = (r.filters() == null ? SegmentFilter.empty() : r.filters()).clean();
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos de la campaña.", e);
        return new Clean(name, f, channel, subject, body);
    }

    private String status(Long id) {
        List<String> l = jdbc.queryForList("select status from campaigns where id = ?", String.class, id);
        if (l.isEmpty()) throw ApiException.notFound("La campaña");
        return l.get(0);
    }
}
