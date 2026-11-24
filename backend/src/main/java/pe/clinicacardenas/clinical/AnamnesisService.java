package pe.clinicacardenas.clinical;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * v2.5: anamnesis por plantillas (Odontología, Odontopediatría, Endodoncia… editables por la clínica) y signos
 * vitales. Las respuestas "Sí" en preguntas marcadas como alerta, y la presión arterial fuera de rango, se muestran
 * como alertas en la ficha antes de atender.
 */
@Service
public class AnamnesisService {
    private static final String ANAMNESIS = "ANAMNESIS";
    private static final String CHOICE = "CHOICE";
    private static final String HISTORIA = "HISTORIA";
    private static final String YES_NO = "YES_NO";
    private static final String DIASTOLIC = "diastolic";
    private static final String QUESTIONS = "questions";
    private static final String SYSTOLIC = "systolic";


    public static final Set<String> TYPES = Set.of("TEXT", YES_NO, CHOICE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;

    public AnamnesisService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    public record Question(String id, String section, String label, String type, List<String> options, Boolean alert) {}

    public record TemplateRequest(String name, String specialty, List<Question> questions, Boolean active) {}

    public record RecordRequest(Long templateId, Map<String, Map<String, Object>> answers) {}

    public record VitalsRequest(LocalDateTime takenAt, Integer systolic, Integer diastolic, Integer heartRate, Integer respRate,
                                BigDecimal temperature, Integer spo2, BigDecimal weight, Integer height, Integer glucose, String notes) {}

    // ------------------------------------------------------------------ plantillas

    public List<Map<String, Object>> templates(boolean all) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, code, name, specialty, questions, active, sort_order as sortOrder, updated_at as updatedAt, updated_by as updatedBy"
                + " from anamnesis_templates" + (all ? "" : " where active = true") + " order by sort_order, id");
        for (Map<String, Object> r : rows) r.put(QUESTIONS, parse((String) r.get(QUESTIONS)));
        return rows;
    }

    @Transactional
    public Map<String, Object> saveTemplate(Long id, TemplateRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = clean(r.name(), 80);
        if (name == null || name.length() < 3) e.put("name", "Escribe el nombre de la plantilla.");
        List<Question> qs = r.questions() == null ? List.of() : r.questions();
        if (qs.isEmpty()) e.put(QUESTIONS, "Agrega al menos una pregunta.");
        if (qs.size() > 80) e.put(QUESTIONS, "Máximo 80 preguntas por plantilla.");
        List<Question> clean = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int n = 0;
        for (Question q : qs) {
            n++;
            String label = clean(q.label(), 150);
            String type = q.type() == null ? "TEXT" : q.type().trim().toUpperCase();
            if (label == null) { e.put(QUESTIONS, "La pregunta " + n + " no tiene texto."); break; }
            if (!TYPES.contains(type)) { e.put(QUESTIONS, "Tipo no válido en la pregunta " + n + "."); break; }
            List<String> options = q.options() == null ? List.of() : q.options().stream().map(o -> clean(o, 80)).filter(Objects::nonNull).distinct().toList();
            if (CHOICE.equals(type) && options.size() < 2) { e.put(QUESTIONS, "La pregunta \"" + label + "\" necesita al menos 2 opciones."); break; }
            String qid = q.id() == null || q.id().isBlank() ? slug(label) : q.id().trim();
            StringBuilder unique = new StringBuilder(qid);
            while (ids.contains(unique.toString())) unique.append('_').append(n);
            qid = unique.toString();
            ids.add(qid);
            clean.add(new Question(qid, clean(q.section(), 60) == null ? "General" : clean(q.section(), 60), label, type,
                    CHOICE.equals(type) ? options : null, YES_NO.equals(type) && Boolean.TRUE.equals(q.alert())));
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la plantilla.", e);
        String questions = write(clean);
        boolean active = r.active() == null || r.active();
        String user = CurrentUser.usernameOrSystem();
        if (id == null) {
            String code = slug(name).toUpperCase();
            Integer used = jdbc.queryForObject("select count(*) from anamnesis_templates where code like ?", Integer.class, code + "%");
            if (used != null && used > 0) code = code + "_" + (used + 1);
            Integer max = jdbc.queryForObject("select coalesce(max(sort_order), 0) from anamnesis_templates", Integer.class);
            jdbc.update("insert into anamnesis_templates (code, name, specialty, questions, active, sort_order, updated_at, updated_by) values (?,?,?,?,?,?,?,?)",
                    code, name, clean(r.specialty(), 60), questions, active, (max == null ? 0 : max) + 1, Dates.now(), user);
            id = jdbc.queryForObject("select id from anamnesis_templates where code = ?", Long.class, code);
            audit.register(ANAMNESIS, id, "CREAR", "Plantilla " + name);
        } else {
            int c = jdbc.update("update anamnesis_templates set name=?, specialty=?, questions=?, active=?, updated_at=?, updated_by=? where id=?",
                    name, clean(r.specialty(), 60), questions, active, Dates.now(), user, id);
            if (c == 0) throw ApiException.notFound("La plantilla");
            audit.register(ANAMNESIS, id, "EDITAR", "Plantilla " + name + " (" + clean.size() + " preguntas)");
        }
        Long fid = id;
        return templates(true).stream().filter(t -> fid.equals(((Number) t.get("id")).longValue())).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ fichas del paciente

    public List<Map<String, Object>> records(Long patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, template_id as templateId, template_name as templateName, questions, answers, alerts,
                       created_at as createdAt, created_by as createdBy, updated_at as updatedAt
                  from anamnesis_records where patient_id = ? order by created_at desc, id desc""", patientId);
        for (Map<String, Object> r : rows) {
            r.put(QUESTIONS, parse((String) r.get(QUESTIONS)));
            r.put("answers", parseAnswers((String) r.get("answers")));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveRecord(Long patientId, Long recordId, RecordRequest r) {
        exists(patientId);
        List<Question> questions;
        String templateName;
        Long templateId;
        if (recordId == null) {
            if (r.templateId() == null) throw ApiException.field("templateId", "Elige la plantilla.");
            List<Map<String, Object>> t = jdbc.queryForList("select id, name, questions from anamnesis_templates where id = ?", r.templateId());
            if (t.isEmpty()) throw ApiException.notFound("La plantilla");
            templateId = r.templateId();
            templateName = (String) t.get(0).get("name");
            questions = parse((String) t.get(0).get(QUESTIONS));
        } else {
            List<Map<String, Object>> old = jdbc.queryForList("select template_id, template_name, questions, created_at, created_by from anamnesis_records where id = ? and patient_id = ?", recordId, patientId);
            if (old.isEmpty()) throw ApiException.notFound("La ficha");
            LocalDateTime created = ldt(old.get(0).get("created_at"));
            // Lo registrado es parte de la historia clínica: se corrige el mismo día; después se crea una ficha nueva.
            if (created.toLocalDate().isBefore(Dates.today())) {
                throw ApiException.badRequest("FICHA_CERRADA", "Esta ficha es de otro día y ya forma parte de la historia. Registra una nueva.");
            }
            templateId = ((Number) old.get(0).get("template_id")).longValue();
            templateName = (String) old.get(0).get("template_name");
            questions = parse((String) old.get(0).get(QUESTIONS));
        }
        Map<String, Map<String, Object>> answers = new LinkedHashMap<>();
        List<String> alerts = new ArrayList<>();
        Map<String, Map<String, Object>> in = r.answers() == null ? Map.of() : r.answers();
        for (Question q : questions) {
            Map<String, Object> a = in.get(q.id());
            if (a == null) continue;
            Object raw = a.get("value");
            String value = raw == null ? null : clean(String.valueOf(raw), 2000);
            String note = clean(a.get("note") == null ? null : String.valueOf(a.get("note")), 300);
            if (YES_NO.equals(q.type()) && value != null && !value.equals("SI") && !value.equals("NO")) value = null;
            if (CHOICE.equals(q.type()) && value != null && (q.options() == null || !q.options().contains(value))) value = null;
            if (value == null && note == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            if (note != null) m.put("note", note);
            answers.put(q.id(), m);
            if (Boolean.TRUE.equals(q.alert()) && "SI".equals(value)) alerts.add(q.label() + (note == null ? "" : " (" + note + ")"));
        }
        if (answers.isEmpty()) throw ApiException.badRequest("SIN_RESPUESTAS", "Responde al menos una pregunta.");
        String alertText = alerts.isEmpty() ? null : String.join(" · ", alerts);
        if (alertText != null && alertText.length() > 500) alertText = alertText.substring(0, 497) + "…";
        String user = CurrentUser.usernameOrSystem();
        if (recordId == null) {
            jdbc.update("insert into anamnesis_records (patient_id, template_id, template_name, questions, answers, alerts, created_at, created_by) values (?,?,?,?,?,?,?,?)",
                    patientId, templateId, templateName, write(questions), write(answers), alertText, Dates.now(), user);
            audit.register(HISTORIA, patientId, ANAMNESIS, templateName + (alertText == null ? "" : " · alertas: " + alertText));
        } else {
            jdbc.update("update anamnesis_records set answers=?, alerts=?, updated_at=? where id=?", write(answers), alertText, Dates.now(), recordId);
            audit.register(HISTORIA, patientId, ANAMNESIS, "Corrigió " + templateName);
        }
        return Map.of("records", records(patientId), "alerts", alerts(patientId));
    }

    // ------------------------------------------------------------------ signos vitales

    public List<Map<String, Object>> vitals(Long patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, taken_at as takenAt, systolic, diastolic, heart_rate as heartRate, resp_rate as respRate, temperature, spo2,
                       weight, height, glucose, notes, created_by as createdBy
                  from vital_signs where patient_id = ? order by taken_at desc, id desc limit 50""", patientId);
        for (Map<String, Object> v : rows) {
            v.put("flags", flags(v));
            Object w = v.get("weight");
            Object h = v.get("height");
            if (w != null && h != null && ((Number) h).intValue() > 0) {
                double m = ((Number) h).doubleValue() / 100.0;
                v.put("bmi", Math.round(((Number) w).doubleValue() / (m * m) * 10) / 10.0);
            }
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> addVitals(Long patientId, VitalsRequest r) {
        exists(patientId);
        Map<String, String> e = new LinkedHashMap<>();
        range(e, SYSTOLIC, r.systolic(), 60, 260, "Presión sistólica entre 60 y 260 mmHg.");
        range(e, DIASTOLIC, r.diastolic(), 30, 160, "Presión diastólica entre 30 y 160 mmHg.");
        if (r.systolic() != null && r.diastolic() != null && r.diastolic() >= r.systolic()) e.put(DIASTOLIC, "La diastólica debe ser menor que la sistólica.");
        if ((r.systolic() == null) != (r.diastolic() == null)) e.put(r.systolic() == null ? SYSTOLIC : DIASTOLIC, "Registra las dos cifras de la presión.");
        range(e, "heartRate", r.heartRate(), 30, 220, "Pulso entre 30 y 220 lpm.");
        range(e, "respRate", r.respRate(), 6, 60, "Frecuencia respiratoria entre 6 y 60 rpm.");
        if (r.temperature() != null && (r.temperature().doubleValue() < 33 || r.temperature().doubleValue() > 43)) e.put("temperature", "Temperatura entre 33 y 43 °C.");
        range(e, "spo2", r.spo2(), 50, 100, "Saturación entre 50 y 100 %.");
        if (r.weight() != null && (r.weight().doubleValue() < 1 || r.weight().doubleValue() > 300)) e.put("weight", "Peso entre 1 y 300 kg.");
        range(e, "height", r.height(), 40, 230, "Talla entre 40 y 230 cm.");
        range(e, "glucose", r.glucose(), 20, 600, "Glucosa entre 20 y 600 mg/dL.");
        boolean any = r.systolic() != null || r.heartRate() != null || r.respRate() != null || r.temperature() != null || r.spo2() != null
                || r.weight() != null || r.height() != null || r.glucose() != null;
        if (!any && e.isEmpty()) e.put(SYSTOLIC, "Registra al menos un signo vital.");
        LocalDateTime at = r.takenAt() == null ? Dates.now() : r.takenAt();
        if (at.isAfter(Dates.now().plusMinutes(5))) e.put("takenAt", "La hora no puede ser futura.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los signos vitales.", e);
        jdbc.update("""
                insert into vital_signs (patient_id, taken_at, systolic, diastolic, heart_rate, resp_rate, temperature, spo2, weight, height, glucose, notes, created_by)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                patientId, at, r.systolic(), r.diastolic(), r.heartRate(), r.respRate(), r.temperature(), r.spo2(), r.weight(), r.height(),
                r.glucose(), clean(r.notes(), 250), CurrentUser.usernameOrSystem());
        audit.register(HISTORIA, patientId, "SIGNOS_VITALES", (r.systolic() == null ? "" : "PA " + r.systolic() + "/" + r.diastolic() + " ")
                + (r.heartRate() == null ? "" : "FC " + r.heartRate()));
        return vitals(patientId);
    }

    @Transactional
    public List<Map<String, Object>> deleteVitals(Long patientId, Long id) {
        List<Map<String, Object>> row = jdbc.queryForList("select taken_at, created_by from vital_signs where id = ? and patient_id = ?", id, patientId);
        if (row.isEmpty()) throw ApiException.notFound("El registro");
        if (!CurrentUser.usernameOrSystem().equalsIgnoreCase((String) row.get(0).get("created_by"))
                || ldt(row.get(0).get("taken_at")).toLocalDate().isBefore(Dates.today())) {
            throw ApiException.badRequest("NO_PERMITIDO", "Solo quien lo registró puede borrarlo, y solo el mismo día.");
        }
        jdbc.update("delete from vital_signs where id = ?", id);
        audit.register(HISTORIA, patientId, "SIGNOS_VITALES", "Borró un registro de signos vitales");
        return vitals(patientId);
    }

    /** Valores fuera de rango para un adulto (referencia: OMS / guías de manejo odontológico del paciente médicamente comprometido). */
    static List<String> flags(Map<String, Object> v) {
        List<String> f = new ArrayList<>();
        Integer s = num(v.get(SYSTOLIC));
        Integer d = num(v.get(DIASTOLIC));
        if (s != null && d != null) {
            if (s >= 180 || d >= 110) f.add("Presión muy alta: postergar tratamiento electivo y derivar");
            else if (s >= 140 || d >= 90) f.add("Presión alta");
            else if (s < 90 || d < 60) f.add("Presión baja");
        }
        Integer hr = num(v.get("heartRate"));
        if (hr != null && (hr > 100 || hr < 50)) f.add(hr > 100 ? "Taquicardia" : "Bradicardia");
        Object t = v.get("temperature");
        if (t != null && ((Number) t).doubleValue() >= 37.5) f.add("Fiebre");
        Integer sat = num(v.get("spo2"));
        if (sat != null && sat < 94) f.add("Saturación baja");
        Integer g = num(v.get("glucose"));
        if (g != null && (g >= 200 || g < 70)) f.add(g >= 200 ? "Glucosa alta" : "Glucosa baja");
        return f;
    }

    // ------------------------------------------------------------------ alertas para la ficha

    /** Alertas vigentes: la última ficha de cada plantilla y los signos vitales de los últimos 30 días. */
    public List<String> alerts(Long patientId) {
        List<String> out = new ArrayList<>(jdbc.queryForList("""
                select r.alerts from anamnesis_records r
                 where r.patient_id = ? and r.alerts is not null
                   and r.id = (select max(x.id) from anamnesis_records x where x.patient_id = r.patient_id and x.template_id = r.template_id)""",
                String.class, patientId));
        List<Map<String, Object>> last = jdbc.queryForList("select systolic, diastolic, heart_rate as heartRate, temperature, spo2, glucose, taken_at"
                + " from vital_signs where patient_id = ? and taken_at >= ? order by taken_at desc limit 1", patientId, Dates.now().minusDays(30));
        if (!last.isEmpty()) {
            List<String> f = flags(last.get(0));
            if (!f.isEmpty()) out.add("Signos vitales: " + String.join(", ", f));
        }
        return out;
    }

    // ------------------------------------------------------------------ utilidades

    private void exists(Long patientId) {
        Integer c = jdbc.queryForObject("select count(*) from patients where id = ?", Integer.class, patientId);
        if (c == null || c == 0) throw ApiException.notFound("El paciente");
    }

    private List<Question> parse(String s) {
        try { return json.readValue(s, new TypeReference<List<Question>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> parseAnswers(String s) {
        try { return json.readValue(s, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String write(Object o) {
        try { return json.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static void range(Map<String, String> e, String field, Integer v, int min, int max, String msg) {
        if (v != null && (v < min || v > max)) e.put(field, msg);
    }

    static LocalDateTime ldt(Object o) {
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(o).replace(' ', 'T'));
    }

    private static Integer num(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    static String slug(String s) {
        if (s == null) return "p";
        String n = java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^a-z0-9]+", "_").replaceAll("(^_)|(_$)", "");
        if (n.isEmpty()) return "p";
        return n.length() > 30 ? n.substring(0, 30) : n;
    }
}
