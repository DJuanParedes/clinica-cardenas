package pe.clinicacardenas.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v2.6: motor de plantillas de mensajes. Cada aviso al paciente (cita registrada, recordatorios, agradecimiento,
 * encuesta, reserva web…) sale de una plantilla editable con variables entre llaves. Al guardar se rechazan
 * variables desconocidas y botones inválidos (WhatsApp admite hasta 3 botones de 20 caracteres).
 */
@Service
public class MessageTemplateService {
    private static final String BUTTONS = "buttons";
    private static final String SUBJECT = "subject";


    /** Variables que se pueden usar en cualquier plantilla y un ejemplo para la vista previa. */
    private static final Map<String, String> VARIABLES_EDIT = new LinkedHashMap<>();
    public static final Map<String, String> VARIABLES = java.util.Collections.unmodifiableMap(VARIABLES_EDIT);
    static {
        VARIABLES_EDIT.put("paciente", "María");
        VARIABLES_EDIT.put("paciente_completo", "María Fernanda Quispe Huamán");
        VARIABLES_EDIT.put("fecha", "Martes 24 de noviembre");
        VARIABLES_EDIT.put("hora", "10:30");
        VARIABLES_EDIT.put("fecha_hora", "Martes 24 de noviembre a las 10:30");
        VARIABLES_EDIT.put("fecha_anterior", "Lunes 23 de noviembre a las 16:00");
        VARIABLES_EDIT.put("odontologo", "Dra. Rosa Cárdenas Vílchez (Ortodoncia)");
        VARIABLES_EDIT.put("especialidad", "Ortodoncia");
        VARIABLES_EDIT.put("motivo", "Control de ortodoncia");
        VARIABLES_EDIT.put("motivo_cancelacion", " (el paciente avisó que viaja)");
        VARIABLES_EDIT.put("clinica", "Clínica Dental Cárdenas");
        VARIABLES_EDIT.put("direccion", "Av. Los Guindos Mz. B lote 18, Chaclacayo");
        VARIABLES_EDIT.put("telefono_clinica", "987 654 000");
        VARIABLES_EDIT.put("enlace_citas", "https://clinica.pe/reservar");
        VARIABLES_EDIT.put("enlace_encuesta", "https://clinica.pe/encuesta/abc123");
    }

    private static final Pattern VAR = Pattern.compile("\\{([a-z_]+)\\}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;

    public MessageTemplateService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    public record Rendered(String code, String subject, String body, List<String> buttons, boolean customized) {}

    public record TemplateRequest(String subject, String body, List<String> buttons) {}

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select t.code, t.category, t.name, t.description, t.subject, t.body, t.default_body as defaultBody, t.buttons,
                       t.updated_at as updatedAt, t.updated_by as updatedBy,
                       (select count(*) from notifications n where n.template_code = t.code) as sent,
                       (select max(n.created_at) from notifications n where n.template_code = t.code) as lastSent
                  from message_templates t order by t.sort_order, t.code""");
        for (Map<String, Object> r : rows) {
            r.put(BUTTONS, buttons((String) r.get(BUTTONS)));
            r.put("customized", !Objects.equals(r.get("body"), r.get("defaultBody")));
        }
        return rows;
    }

    public Map<String, Object> get(String code) {
        return list().stream().filter(t -> code.equals(t.get("code"))).findFirst().orElseThrow(() -> ApiException.notFound("La plantilla"));
    }

    @Transactional
    public Map<String, Object> update(String code, TemplateRequest r) {
        get(code);
        Map<String, String> e = new LinkedHashMap<>();
        String subject = r.subject() == null ? "" : r.subject().trim();
        String body = r.body() == null ? "" : r.body().replace("\r\n", "\n").trim();
        if (subject.length() < 3 || subject.length() > 150) e.put(SUBJECT, "El asunto debe tener entre 3 y 150 caracteres.");
        if (body.length() < 10) e.put("body", "Escribe el mensaje (mínimo 10 caracteres).");
        else if (body.length() > 1000) e.put("body", "Máximo 1000 caracteres: WhatsApp corta los mensajes con botones más largos.");
        List<String> unknown = unknownVars(subject + " " + body);
        if (!unknown.isEmpty()) e.put("body", "Variables que no existen: " + String.join(", ", unknown.stream().map(v -> "{" + v + "}").toList())
                + ". Usa solo las de la lista.");
        List<String> btn = new ArrayList<>();
        if (r.buttons() != null) {
            for (String b : r.buttons()) {
                if (b == null || b.isBlank()) continue;
                String t = b.trim();
                if (t.length() > 20) { e.put(BUTTONS, "Cada botón puede tener hasta 20 caracteres (\"" + t + "\")."); break; }
                if (!btn.contains(t)) btn.add(t);
            }
            if (btn.size() > 3) e.put(BUTTONS, "WhatsApp permite hasta 3 botones.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la plantilla.", e);
        jdbc.update("update message_templates set subject=?, body=?, buttons=?, updated_at=?, updated_by=? where code=?",
                subject, body, btn.isEmpty() ? null : write(btn), Dates.now(), CurrentUser.usernameOrSystem(), code);
        audit.register("PLANTILLA", null, "EDITAR", code);
        return get(code);
    }

    @Transactional
    public Map<String, Object> restore(String code) {
        Map<String, Object> t = get(code);
        jdbc.update("update message_templates set body = default_body, updated_at=?, updated_by=? where code=?", Dates.now(), CurrentUser.usernameOrSystem(), code);
        audit.register("PLANTILLA", null, "RESTAURAR", (String) t.get("name"));
        return get(code);
    }

    /** Vista previa con datos de ejemplo (o el texto que se está editando, antes de guardarlo). */
    public Map<String, Object> preview(String code, String body, String subject) {
        Map<String, Object> t = get(code);
        String b = body == null ? (String) t.get("body") : body;
        String s = subject == null ? (String) t.get(SUBJECT) : subject;
        return Map.of(SUBJECT, fill(s, VARIABLES), "body", fill(b, VARIABLES), "unknown", unknownVars(s + " " + b));
    }

    public Rendered render(String code, Map<String, String> vars) {
        List<Map<String, Object>> r = jdbc.queryForList("select code, subject, body, default_body, buttons from message_templates where code = ?", code);
        if (r.isEmpty()) throw new IllegalStateException("Plantilla inexistente: " + code);
        Map<String, Object> t = r.get(0);
        return new Rendered(code, fill((String) t.get(SUBJECT), vars), fill((String) t.get("body"), vars),
                buttons((String) t.get(BUTTONS)), !Objects.equals(t.get("body"), t.get("default_body")));
    }

    /** Envíos de una plantilla (o de todas): base del historial. */
    public List<Map<String, Object>> history(String code, String status, String q, int limit) {
        StringBuilder sql = new StringBuilder("""
                select n.id, n.template_code as templateCode, n.type, n.operation, n.channel, n.destination, n.status, n.scheduled_for as scheduledFor,
                       n.sent_at as sentAt, n.created_at as createdAt, n.last_error as lastError, n.body, n.appointment_id as appointmentId,
                       n.patient_id as patientId, concat(p.first_name, ' ', p.last_name) as patientName, t.name as templateName
                  from notifications n left join patients p on p.id = n.patient_id left join message_templates t on t.code = n.template_code
                 where n.operation <> 'ANULAR'""");
        List<Object> args = new ArrayList<>();
        if (code != null && !code.isBlank()) { sql.append(" and n.template_code = ?"); args.add(code); }
        if (status != null && !status.isBlank()) { sql.append(" and n.status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) {
            sql.append(" and (lower(concat(p.first_name, ' ', p.last_name)) like ? or n.destination like ?)");
            String like = "%" + q.trim().toLowerCase() + "%";
            args.add(like); args.add(like);
        }
        sql.append(" order by n.id desc limit ").append(Math.clamp(limit, 1, 300));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public static String fill(String text, Map<String, String> vars) {
        if (text == null) return "";
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? (VARIABLES.containsKey(m.group(1)) ? "" : m.group()) : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static List<String> unknownVars(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = VAR.matcher(text == null ? "" : text);
        while (m.find()) if (!VARIABLES.containsKey(m.group(1)) && !out.contains(m.group(1))) out.add(m.group(1));
        // llaves con mayúsculas, espacios o tildes tampoco son variables válidas
        Matcher odd = Pattern.compile("\\{([^}]*)\\}").matcher(text == null ? "" : text);
        while (odd.find()) if (!odd.group(1).matches("[a-z_]+") && !out.contains(odd.group(1))) out.add(odd.group(1));
        return out;
    }

    private List<String> buttons(String s) {
        if (s == null || s.isBlank()) return List.of();
        try { return json.readValue(s, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String write(Object o) {
        try { return json.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
