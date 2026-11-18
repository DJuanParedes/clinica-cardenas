package pe.clinicacardenas.patient;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.security.CurrentUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documentos legales versionados y registro de cada aceptación (Ley N.° 29733 y su Reglamento, D.S. 016-2024-JUS):
 * qué versión se aceptó, por qué canal, quién aceptó (el titular o, si es menor, su representante legal),
 * quién lo registró y, si fue presencial, la copia firmada escaneada.
 */
@Service
public class ConsentService {

    private final JdbcTemplate jdbc;
    private final PatientRepository patients;
    private final FileService files;
    private final AuditService audit;

    public ConsentService(JdbcTemplate jdbc, PatientRepository patients, FileService files, AuditService audit) {
        this.jdbc = jdbc;
        this.patients = patients;
        this.files = files;
        this.audit = audit;
    }

    public Map<String, Object> document(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select code, version, title, body, published_at as publishedAt from legal_documents where code = ? and active order by published_at desc limit 1",
                code == null ? "" : code.trim().toUpperCase());
        if (rows.isEmpty()) throw ApiException.notFound("El documento");
        return rows.get(0);
    }

    public String currentVersion(String code) {
        return String.valueOf(document(code).get("version"));
    }

    /** Quien acepta: el paciente o, si es menor de edad, su representante legal. */
    static String acceptedBy(Patient p) {
        if (p.isMinor() && p.guardianName != null) {
            String rel = switch (p.guardianRelationship == null ? "" : p.guardianRelationship) {
                case "PADRE" -> "padre";
                case "MADRE" -> "madre";
                default -> "tutor legal";
            };
            return p.guardianName + " (" + rel + ", " + p.guardianDocType + " " + p.guardianDocNumber + ")";
        }
        return p.getFullName();
    }

    /** Aceptación registrada por el personal en recepción (presencial). */
    @Transactional
    public void recordInitial(Patient p) {
        record(p, "PRIVACIDAD", "PRESENCIAL", CurrentUser.usernameOrSystem(), null);
    }

    @Transactional
    public void record(Patient p, String code, String channel, String registeredBy, String ip) {
        String version = currentVersion(code);
        jdbc.update("""
                insert into consent_records (patient_id, doc_code, doc_version, channel, accepted_by, registered_by, ip, accepted_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, p.id, code, version, channel, acceptedBy(p), registeredBy, ip, Dates.now());
        if ("PRIVACIDAD".equals(code)) {
            p.consentVersion = version;
            p.consentChannel = channel;
            p.consentBy = registeredBy;
            p.dataConsent = true;
            if (p.dataConsentAt == null) p.dataConsentAt = Dates.now();
            patients.save(p);
        }
        audit.record("PACIENTE", p.id, "CONSENTIMIENTO", code + " v" + version + " · " + channel + " · aceptó " + acceptedBy(p));
    }

    /** Copia escaneada o foto del consentimiento firmado en recepción. */
    @Transactional
    public Map<String, Object> attachSigned(Long patientId, MultipartFile file) {
        Patient p = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        if (!p.dataConsent) throw ApiException.conflict("SIN_CONSENTIMIENTO", "Primero registra la aceptación de la política de privacidad.");
        PatientFile f = files.upload(patientId, file, "CONSENTIMIENTO",
                "Consentimiento de datos personales firmado (v" + p.consentVersion + ")", null, null, false);
        p.consentFileId = f.id;
        patients.save(p);
        jdbc.update("""
                update consent_records set file_id = ? where id = (
                  select id from (select id from consent_records where patient_id = ? and doc_code = 'PRIVACIDAD'
                                   order by accepted_at desc limit 1) t)
                """, f.id, patientId);
        audit.record("PACIENTE", p.id, "CONSENTIMIENTO_FIRMADO", "Adjuntó la copia firmada");
        return history(patientId);
    }

    public Map<String, Object> history(Long patientId) {
        Patient p = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accepted", p.dataConsent);
        m.put("version", p.consentVersion);
        m.put("currentVersion", currentVersion("PRIVACIDAD"));
        m.put("signedFileId", p.consentFileId);
        m.put("acceptedBy", p.dataConsent ? acceptedBy(p) : null);
        m.put("records", jdbc.queryForList("""
                select doc_code as code, doc_version as version, channel, accepted_by as acceptedBy, registered_by as registeredBy,
                       file_id as fileId, accepted_at as acceptedAt
                  from consent_records where patient_id = ? order by accepted_at desc
                """, patientId));
        return m;
    }
}
