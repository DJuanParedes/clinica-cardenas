package pe.clinicacardenas.patient;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.billing.RucValidator;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.util.*;

/**
 * v2.5: datos que la clínica usa para medir y facturar: fuente de captación, etiquetas, N° de historia clínica,
 * datos fiscales y familiares vinculados. Se guardan aparte del registro (HU-01) para no tocar sus validaciones.
 */
@Service
public class PatientProfileService {
    private static final String FAMILIAR = "FAMILIAR";
    private static final String PACIENTE = "PACIENTE";
    private static final String FISCALRUC = "fiscalRuc";
    private static final String HCNUMBER = "hcNumber";


    /** De dónde llegan los pacientes: base para segmentar campañas y medir qué canal funciona. */
    public static final List<Map<String, String>> SOURCES = List.of(
            src("RECOMENDACION", "Recomendación de un paciente o familiar"),
            src("FACEBOOK", "Facebook"),
            src("INSTAGRAM", "Instagram"),
            src("TIKTOK", "TikTok"),
            src("GOOGLE", "Google / Google Maps"),
            src("WEB", "Página web"),
            src("WHATSAPP", "WhatsApp"),
            src("VOLANTE", "Volante, afiche o publicidad"),
            src("PASO_POR_LA_CLINICA", "Pasó por la clínica"),
            src("CONVENIO", "Convenio o empresa"),
            src("CAMPANA", "Campaña de salud"),
            src("OTRO", "Otro"));

    public static final List<String> RELATIONSHIPS = List.of("PADRE", "MADRE", "HIJO", "HIJA", "CONYUGE", "HERMANO", "ABUELO", "NIETO", "TIO", "TUTOR", "OTRO");

    private static Map<String, String> src(String code, String label) {
        return Map.of("code", code, "label", label);
    }

    private final JdbcTemplate jdbc;
    private final PatientService patients;
    private final PatientRepository repo;
    private final AuditService audit;

    public PatientProfileService(JdbcTemplate jdbc, PatientService patients, PatientRepository repo, AuditService audit) {
        this.jdbc = jdbc;
        this.patients = patients;
        this.repo = repo;
        this.audit = audit;
    }

    public record ProfileRequest(String source, String sourceDetail, String hcNumber, List<String> tags,
                                 String fiscalRuc, String fiscalName, String fiscalAddress) {}

    public record RelationRequest(Long relatedPatientId, String fullName, String docType, String docNumber,
                                  String relationship, String phone, String email, Boolean isGuardian, Boolean pays,
                                  String notes) {}

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Long patientId) {
        Patient p = patients.get(patientId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", p.source);
        m.put("sourceLabel", label(p.source));
        m.put("sourceDetail", p.sourceDetail);
        m.put(HCNUMBER, p.hcNumber);
        m.put(FISCALRUC, p.fiscalRuc);
        m.put("fiscalName", p.fiscalName);
        m.put("fiscalAddress", p.fiscalAddress);
        m.put("tags", tags(patientId));
        m.put("relations", relations(patientId));
        return m;
    }

    public List<String> tags(Long patientId) {
        return jdbc.queryForList("select tag from patient_tags where patient_id = ? order by tag", String.class, patientId);
    }

    /** Etiquetas en uso con cuántos pacientes tiene cada una (para sugerir al escribir y para filtrar). */
    public List<Map<String, Object>> allTags() {
        return jdbc.queryForList("select tag, count(*) as patients from patient_tags group by tag order by count(*) desc, tag");
    }

    /** Familiares propios y, además, los pacientes que registraron a este como su familiar (vínculo inverso). */
    public List<Map<String, Object>> relations(Long patientId) {
        List<Map<String, Object>> own = jdbc.queryForList("""
                select r.id, r.related_patient_id as relatedPatientId, r.full_name as fullName, r.doc_type as docType,
                       r.doc_number as docNumber, r.relationship, r.phone, r.email, r.is_guardian as isGuardian, r.pays, r.notes,
                       p.hc_number as relatedHc, false as inverse
                  from patient_relations r left join patients p on p.id = r.related_patient_id
                 where r.patient_id = ? order by r.is_guardian desc, r.id""", patientId);
        List<Map<String, Object>> inverse = jdbc.queryForList("""
                select r.id, r.patient_id as relatedPatientId, concat(p.first_name, ' ', p.last_name) as fullName, p.doc_type as docType,
                       p.doc_number as docNumber, r.relationship, p.phone, p.email, false as isGuardian, false as pays, null as notes,
                       p.hc_number as relatedHc, true as inverse
                  from patient_relations r join patients p on p.id = r.patient_id
                 where r.related_patient_id = ? order by r.id""", patientId);
        List<Map<String, Object>> all = new ArrayList<>(own);
        all.addAll(inverse);
        return all;
    }

    @Transactional
    public Map<String, Object> updateProfile(Long patientId, ProfileRequest r) {
        Patient p = patients.get(patientId);
        Map<String, String> e = new LinkedHashMap<>();
        String source = blank(r.source()) ? null : r.source().trim().toUpperCase();
        if (source != null && SOURCES.stream().noneMatch(x -> x.get("code").equals(source))) e.put("source", "Elige una opción de la lista.");
        String detail = trim(r.sourceDetail(), 120);
        String hc = blank(r.hcNumber()) ? p.hcNumber : r.hcNumber().trim().toUpperCase();
        if (hc != null && !hc.matches("^[A-Z0-9-]{1,20}$")) e.put(HCNUMBER, "Usa solo números, letras y guiones (máx. 20).");
        else if (hc != null && !hc.equals(p.hcNumber)) {
            Integer used = jdbc.queryForObject("select count(*) from patients where hc_number = ? and id <> ?", Integer.class, hc, patientId);
            if (used != null && used > 0) e.put(HCNUMBER, "Ese N° de historia ya lo tiene otro paciente.");
        }
        String ruc = blank(r.fiscalRuc()) ? null : r.fiscalRuc().replaceAll("\\s", "");
        String fiscalName = trim(r.fiscalName(), 200);
        if (ruc != null && !RucValidator.isValid(ruc)) e.put(FISCALRUC, "El RUC no es válido (11 dígitos y dígito verificador).");
        if (ruc != null && fiscalName == null) e.put("fiscalName", "Escribe la razón social para la factura.");
        if (ruc == null && fiscalName != null) e.put(FISCALRUC, "Falta el RUC.");
        List<String> tags = new ArrayList<>();
        if (r.tags() != null) {
            for (String t : r.tags()) {
                if (blank(t)) continue;
                String tag = t.trim().replaceAll("\\s+", " ");
                if (tag.length() > 40) { e.put("tags", "Cada etiqueta puede tener hasta 40 caracteres."); break; }
                if (tags.stream().noneMatch(x -> x.equalsIgnoreCase(tag))) tags.add(tag);
            }
            if (tags.size() > 15) e.put("tags", "Máximo 15 etiquetas por paciente.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", e);

        List<String> changes = new ArrayList<>();
        if (!Objects.equals(p.source, source) || !Objects.equals(p.sourceDetail, detail)) changes.add("fuente de captación");
        if (!Objects.equals(p.hcNumber, hc)) changes.add("N° de historia " + p.hcNumber + " → " + hc);
        if (!Objects.equals(p.fiscalRuc, ruc) || !Objects.equals(p.fiscalName, fiscalName)) changes.add("datos fiscales");
        p.source = source;
        p.sourceDetail = detail;
        p.hcNumber = hc;
        p.fiscalRuc = ruc;
        p.fiscalName = fiscalName;
        p.fiscalAddress = ruc == null ? null : trim(r.fiscalAddress(), 250);
        p.updatedAt = Dates.now();
        p.updatedBy = CurrentUser.usernameOrSystem();
        repo.save(p);
        if (r.tags() != null) {
            List<String> before = tags(patientId);
            jdbc.update("delete from patient_tags where patient_id = ?", patientId);
            for (String t : tags) jdbc.update("insert into patient_tags (patient_id, tag) values (?, ?)", patientId, t);
            if (!new HashSet<>(before).equals(new HashSet<>(tags))) changes.add("etiquetas");
        }
        if (!changes.isEmpty()) audit.register(PACIENTE, patientId, "EDITAR", "Actualizó " + String.join(", ", changes));
        return profile(patientId);
    }

    // ------------------------------------------------------------------ familiares

    @Transactional
    public Map<String, Object> addRelation(Long patientId, RelationRequest r) {
        Patient p = patients.get(patientId);
        Values v = validate(patientId, r, null);
        jdbc.update("""
                insert into patient_relations (patient_id, related_patient_id, full_name, doc_type, doc_number, relationship, phone, email,
                  is_guardian, pays, notes, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                patientId, v.relatedId, v.name, v.docType, v.docNumber, v.relationship, v.phone, v.email, v.guardian, v.pays, v.notes,
                Dates.now(), CurrentUser.usernameOrSystem());
        audit.register(PACIENTE, patientId, FAMILIAR, "Vinculó a " + v.name + " (" + v.relationship.toLowerCase() + ") con " + p.getFullName());
        return profile(patientId);
    }

    @Transactional
    public Map<String, Object> updateRelation(Long patientId, Long relationId, RelationRequest r) {
        owned(patientId, relationId);
        Values v = validate(patientId, r, relationId);
        jdbc.update("""
                update patient_relations set related_patient_id=?, full_name=?, doc_type=?, doc_number=?, relationship=?, phone=?, email=?,
                  is_guardian=?, pays=?, notes=? where id = ?""",
                v.relatedId, v.name, v.docType, v.docNumber, v.relationship, v.phone, v.email, v.guardian, v.pays, v.notes, relationId);
        audit.register(PACIENTE, patientId, FAMILIAR, "Actualizó el familiar " + v.name);
        return profile(patientId);
    }

    @Transactional
    public Map<String, Object> deleteRelation(Long patientId, Long relationId) {
        String name = owned(patientId, relationId);
        jdbc.update("delete from patient_relations where id = ?", relationId);
        audit.register(PACIENTE, patientId, FAMILIAR, "Quitó el vínculo con " + name);
        return profile(patientId);
    }

    private String owned(Long patientId, Long relationId) {
        List<String> n = jdbc.queryForList("select full_name from patient_relations where id = ? and patient_id = ?", String.class, relationId, patientId);
        if (n.isEmpty()) throw ApiException.notFound("El familiar");
        return n.get(0);
    }

    private record Values(Long relatedId, String name, String docType, String docNumber, String relationship, String phone,
                          String email, boolean guardian, boolean pays, String notes) {}

    private Values validate(Long patientId, RelationRequest r, Long relationId) {
        Map<String, String> e = new LinkedHashMap<>();
        Long relatedId = r.relatedPatientId();
        String name = trim(r.fullName(), 150);
        String docType = blank(r.docType()) ? null : r.docType().trim().toUpperCase();
        String docNumber = blank(r.docNumber()) ? null : PatientValidator.normalizeDoc(r.docNumber());
        String phone = blank(r.phone()) ? null : PatientValidator.normalizePhone(r.phone());
        String email = blank(r.email()) ? null : r.email().trim().toLowerCase();
        if (relatedId != null) {
            if (relatedId.equals(patientId)) e.put("relatedPatientId", "No se puede vincular al paciente consigo mismo.");
            else {
                Patient rel = patients.get(relatedId);
                name = rel.getFullName();
                docType = rel.docType;
                docNumber = rel.docNumber;
                if (phone == null) phone = rel.phone;
                if (email == null) email = rel.email;
                Integer dup = jdbc.queryForObject("select count(*) from patient_relations where patient_id = ? and related_patient_id = ? and id <> ?",
                        Integer.class, patientId, relatedId, relationId == null ? -1L : relationId);
                if (dup != null && dup > 0) e.put("relatedPatientId", "Ese familiar ya está vinculado.");
            }
        }
        if (name == null || name.length() < 3) e.put("fullName", "Escribe el nombre completo del familiar.");
        String rel = blank(r.relationship()) ? null : r.relationship().trim().toUpperCase();
        if (rel == null || !RELATIONSHIPS.contains(rel)) e.put("relationship", "Elige el parentesco.");
        if ("DNI".equals(docType) && docNumber != null && !docNumber.matches("^\\d{8}$")) e.put("docNumber", "El DNI tiene 8 dígitos.");
        if (phone != null && !phone.matches("^9\\d{8}$")) e.put("phone", "Celular de 9 dígitos que empiece con 9.");
        if (email != null && !pe.clinicacardenas.common.Emails.valid(email)) e.put("email", "Correo no válido.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del familiar.", e);
        return new Values(relatedId, name, docType, docNumber, rel, phone, email, Boolean.TRUE.equals(r.isGuardian()),
                Boolean.TRUE.equals(r.pays()), trim(r.notes(), 200));
    }

    // ------------------------------------------------------------------ utilidades

    public static String label(String code) {
        if (code == null) return null;
        return SOURCES.stream().filter(x -> x.get("code").equals(code)).map(x -> x.get("label")).findFirst().orElse(code);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s, int max) {
        if (blank(s)) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}
