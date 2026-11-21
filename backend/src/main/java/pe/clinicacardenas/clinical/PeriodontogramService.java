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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * v2.5: periodontograma (examen periodontal completo). Por pieza FDI: ausente, implante, movilidad (0–3), furca
 * (0–3, molares) y en cada cara (vestibular y palatina/lingual) tres sitios —distal, medio y mesial— con margen
 * gingival (mm; positivo = recesión), profundidad de sondaje (mm), sangrado al sondaje y placa.
 * Calcula índice de placa (O'Leary, %IP), sangrado al sondaje (%SAS), sondaje medio y sitios con bolsa (≥ 4 mm).
 */
@Service
public class PeriodontogramService {

    public static final List<Integer> TEETH = List.of(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28,
            48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38);
    private static final Set<Integer> MOLARS = Set.of(18, 17, 16, 28, 27, 26, 38, 37, 36, 48, 47, 46, 14, 24);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;

    public PeriodontogramService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    public static class Face {
        public List<Integer> mg = new ArrayList<>(List.of(0, 0, 0));
        public List<Integer> ps = new ArrayList<>(List.of(0, 0, 0));
        public List<Boolean> bleed = new ArrayList<>(List.of(false, false, false));
        public List<Boolean> plaque = new ArrayList<>(List.of(false, false, false));
    }

    public static class Tooth {
        public boolean missing;
        public boolean implant;
        public int mobility;
        public int furcation;
        public Face v = new Face();
        public Face l = new Face();
    }

    public record ExamRequest(LocalDate examDate, Map<String, Tooth> data, String notes) {}

    public List<Map<String, Object>> list(Long patientId) {
        return jdbc.queryForList("""
                select id, exam_date as examDate, dentist_name as dentistName, plaque_index as plaqueIndex, bleeding_index as bleedingIndex,
                       mean_depth as meanDepth, deep_sites as deepSites, teeth_present as teethPresent, notes, created_at as createdAt,
                       created_by as createdBy, updated_at as updatedAt
                  from periodontograms where patient_id = ? order by exam_date desc, id desc""", patientId);
    }

    public Map<String, Object> get(Long patientId, Long id) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                select id, exam_date as examDate, dentist_name as dentistName, data, plaque_index as plaqueIndex, bleeding_index as bleedingIndex,
                       mean_depth as meanDepth, deep_sites as deepSites, teeth_present as teethPresent, notes, created_at as createdAt,
                       created_by as createdBy, updated_at as updatedAt
                  from periodontograms where id = ? and patient_id = ?""", id, patientId);
        if (r.isEmpty()) throw ApiException.notFound("El periodontograma");
        Map<String, Object> m = r.get(0);
        m.put("data", parse((String) m.get("data")));
        return m;
    }

    @Transactional
    public Map<String, Object> save(Long patientId, Long id, ExamRequest r) {
        Integer exists = jdbc.queryForObject("select count(*) from patients where id = ?", Integer.class, patientId);
        if (exists == null || exists == 0) throw ApiException.notFound("El paciente");
        LocalDate date = r.examDate() == null ? Dates.today() : r.examDate();
        if (date.isAfter(Dates.today())) throw ApiException.field("examDate", "La fecha no puede ser futura.");
        Map<String, Tooth> data = normalize(r.data());
        Indices ix = indices(data);
        if (ix.sites == 0) throw ApiException.badRequest("SIN_PIEZAS", "Marca al menos una pieza presente.");
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim().substring(0, Math.min(500, r.notes().trim().length()));
        String user = CurrentUser.usernameOrSystem();
        String dentist = jdbc.queryForList("select full_name from users where lower(username) = lower(?)", String.class, user).stream().findFirst().orElse(user);
        String payload = write(data);
        if (id == null) {
            jdbc.update("""
                    insert into periodontograms (patient_id, exam_date, dentist_name, data, plaque_index, bleeding_index, mean_depth, deep_sites,
                      teeth_present, notes, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    patientId, date, dentist, payload, ix.plaque, ix.bleeding, ix.meanDepth, ix.deepSites, ix.teeth, notes, Dates.now(), user);
            id = jdbc.queryForObject("select max(id) from periodontograms where patient_id = ?", Long.class, patientId);
            audit.record("HISTORIA", patientId, "PERIODONTOGRAMA", "Examen del " + date + " · IP " + ix.plaque + "% · SAS " + ix.bleeding + "%");
        } else {
            List<Map<String, Object>> old = jdbc.queryForList("select created_at from periodontograms where id = ? and patient_id = ?", id, patientId);
            if (old.isEmpty()) throw ApiException.notFound("El periodontograma");
            if (AnamnesisService.ldt(old.get(0).get("created_at")).toLocalDate().isBefore(Dates.today())) {
                throw ApiException.badRequest("EXAMEN_CERRADO", "Este examen es de otro día y ya forma parte de la historia. Registra uno nuevo para comparar.");
            }
            jdbc.update("""
                    update periodontograms set exam_date=?, data=?, plaque_index=?, bleeding_index=?, mean_depth=?, deep_sites=?, teeth_present=?,
                      notes=?, updated_at=? where id=?""",
                    date, payload, ix.plaque, ix.bleeding, ix.meanDepth, ix.deepSites, ix.teeth, notes, Dates.now(), id);
            audit.record("HISTORIA", patientId, "PERIODONTOGRAMA", "Corrigió el examen del " + date);
        }
        return get(patientId, id);
    }

    /** Solo piezas FDI válidas y valores en rango; las caras de una pieza ausente no cuentan. */
    static Map<String, Tooth> normalize(Map<String, Tooth> in) {
        Map<String, Tooth> out = new LinkedHashMap<>();
        Map<String, String> e = new LinkedHashMap<>();
        for (Integer n : TEETH) {
            Tooth t = in == null ? null : in.get(String.valueOf(n));
            if (t == null) t = new Tooth();
            if (t.mobility < 0 || t.mobility > 3) e.put("data", "Movilidad de la pieza " + n + " entre 0 y 3.");
            if (t.furcation < 0 || t.furcation > 3) e.put("data", "Furca de la pieza " + n + " entre 0 y 3.");
            if (!MOLARS.contains(n)) t.furcation = 0;
            for (Face f : List.of(fix(t.v), fix(t.l))) {
                for (int i = 0; i < 3; i++) {
                    if (f.ps.get(i) < 0 || f.ps.get(i) > 15) e.put("data", "Sondaje de la pieza " + n + " entre 0 y 15 mm.");
                    if (f.mg.get(i) < -5 || f.mg.get(i) > 15) e.put("data", "Margen gingival de la pieza " + n + " entre -5 y 15 mm.");
                }
            }
            t.v = fix(t.v);
            t.l = fix(t.l);
            out.put(String.valueOf(n), t);
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", e.values().iterator().next(), e);
        return out;
    }

    private static Face fix(Face f) {
        Face o = f == null ? new Face() : f;
        o.mg = pad(o.mg, 0);
        o.ps = pad(o.ps, 0);
        o.bleed = pad(o.bleed, false);
        o.plaque = pad(o.plaque, false);
        return o;
    }

    private static <T> List<T> pad(List<T> l, T def) {
        List<T> o = new ArrayList<>(l == null ? List.of() : l);
        for (int i = 0; i < o.size(); i++) if (o.get(i) == null) o.set(i, def);
        while (o.size() < 3) o.add(def);
        return new ArrayList<>(o.subList(0, 3));
    }

    record Indices(int teeth, int sites, BigDecimal plaque, BigDecimal bleeding, BigDecimal meanDepth, int deepSites) {}

    static Indices indices(Map<String, Tooth> data) {
        int teeth = 0, sites = 0, plaque = 0, bleed = 0, deep = 0, depthSum = 0;
        for (Tooth t : data.values()) {
            if (t.missing) continue;
            teeth++;
            for (Face f : List.of(t.v, t.l)) {
                for (int i = 0; i < 3; i++) {
                    sites++;
                    if (Boolean.TRUE.equals(f.plaque.get(i))) plaque++;
                    if (Boolean.TRUE.equals(f.bleed.get(i))) bleed++;
                    int ps = f.ps.get(i);
                    depthSum += ps;
                    if (ps >= 4) deep++;
                }
            }
        }
        return new Indices(teeth, sites, pct(plaque, sites), pct(bleed, sites),
                sites == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(depthSum).divide(BigDecimal.valueOf(sites), 2, RoundingMode.HALF_UP), deep);
    }

    private static BigDecimal pct(int part, int total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Tooth> parse(String s) {
        try { return json.readValue(s, new TypeReference<LinkedHashMap<String, Tooth>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private String write(Object o) {
        try { return json.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
