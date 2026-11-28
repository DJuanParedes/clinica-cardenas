package pe.clinicacardenas.clinical;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.agenda.AttentionLink;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.security.CurrentUser;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v2.17: recetas estructuradas. Cada medicamento queda registrado (dosis, frecuencia, días) para consultar el
 * historial del paciente; además se genera el PDF de la receta en sus archivos. Una receta no se borra: se anula.
 */
@RestController
@RequestMapping("/api")
public class PrescriptionService {
    private final JdbcTemplate jdbc;
    private final FileService files;
    private final AuditService audit;

    public PrescriptionService(JdbcTemplate jdbc, FileService files, AuditService audit) {
        this.jdbc = jdbc;
        this.files = files;
        this.audit = audit;
    }

    public record Item(String drug, String presentation, String dose, String frequency, Integer days, String route, String instructions) {}
    public record Request(List<Item> items, String indications, Boolean shareWithPatient) {}

    @GetMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
    public List<Map<String, Object>> list(@PathVariable Long patientId) {
        List<Map<String, Object>> rx = jdbc.queryForList("""
                select r.id, r.indications, r.file_id as fileId, r.appointment_id as appointmentId, r.created_at as createdAt,
                       r.created_by as createdBy, r.voided_at as voidedAt, r.voided_by as voidedBy, r.void_reason as voidReason,
                       u.full_name as dentist
                  from prescriptions r left join dentists d on d.id = r.dentist_id left join users u on u.id = d.user_id
                 where r.patient_id = ? order by r.created_at desc, r.id desc""", patientId);
        for (Map<String, Object> r : rx) {
            r.put("items", jdbc.queryForList("""
                    select drug, presentation, dose, frequency, days, route, instructions
                      from prescription_items where prescription_id = ? order by id""", r.get("id")));
        }
        return rx;
    }

    @PostMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
    @Transactional
    public Map<String, Object> create(@PathVariable Long patientId, @RequestBody Request r) {
        if (r.items() == null || r.items().isEmpty()) throw ApiException.field("items", "Agrega al menos un medicamento.");
        if (r.items().size() > 15) throw ApiException.field("items", "Máximo 15 medicamentos por receta.");
        List<Item> clean = new ArrayList<>();
        for (Item i : r.items()) {
            String drug = trim(i.drug(), 120), dose = trim(i.dose(), 60), freq = trim(i.frequency(), 60);
            if (drug == null || drug.length() < 2) throw ApiException.field("items", "Escribe el nombre de cada medicamento.");
            if (dose == null) throw ApiException.field("items", "Indica la dosis de " + drug + ".");
            if (freq == null) throw ApiException.field("items", "Indica cada cuánto se toma " + drug + ".");
            if (i.days() != null && (i.days() < 1 || i.days() > 365)) throw ApiException.field("items", "Los días de " + drug + " deben estar entre 1 y 365.");
            clean.add(new Item(drug, trim(i.presentation(), 80), dose, freq, i.days(), trim(i.route(), 30), trim(i.instructions(), 200)));
        }
        String indications = trim(r.indications(), 1000);
        Long dentistId = jdbc.queryForList("select d.id from dentists d join users u on u.id = d.user_id where u.username = ?",
                Long.class, CurrentUser.usernameOrSystem()).stream().findFirst().orElse(null);
        Long appointmentId = AttentionLink.current(jdbc, patientId);

        StringBuilder body = new StringBuilder();
        int n = 1;
        for (Item i : clean) {
            body.append(n++).append(". ").append(i.drug()).append(i.presentation() == null ? "" : " — " + i.presentation()).append('\n')
                .append("   ").append(i.dose()).append(", ").append(i.frequency())
                .append(i.days() == null ? "" : ", por " + i.days() + (i.days() == 1 ? " día" : " días"))
                .append(i.route() == null ? "" : " (vía " + i.route().toLowerCase() + ")").append('\n');
            if (i.instructions() != null) body.append("   ").append(i.instructions()).append('\n');
        }
        if (indications != null) body.append('\n').append("Indicaciones: ").append(indications).append('\n');
        PatientFile pdf = files.document(patientId, new FileService.DocumentRequest("RECETA", "Receta médica", body.toString(),
                null, null, Boolean.TRUE.equals(r.shareWithPatient())));

        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into prescriptions (patient_id, dentist_id, appointment_id, indications, file_id, created_at, created_by)
                    values (?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, patientId);
            ps.setObject(2, dentistId);
            ps.setObject(3, appointmentId);
            ps.setString(4, indications);
            ps.setObject(5, pdf.id);
            ps.setObject(6, Dates.now());
            ps.setString(7, CurrentUser.usernameOrSystem());
            return ps;
        }, kh);
        long id = kh.getKey().longValue();
        for (Item i : clean) {
            jdbc.update("""
                    insert into prescription_items (prescription_id, drug, presentation, dose, frequency, days, route, instructions)
                    values (?, ?, ?, ?, ?, ?, ?, ?)""", id, i.drug(), i.presentation(), i.dose(), i.frequency(), i.days(), i.route(), i.instructions());
        }
        audit.register("HISTORIA", patientId, "RECETA", clean.stream().map(Item::drug).reduce((a, b) -> a + ", " + b).orElse(""));
        return Map.of("id", id, "fileId", pdf.id);
    }

    @PostMapping("/prescriptions/{id}/void")
    @PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
    @Transactional
    public Map<String, Object> voidRx(@PathVariable Long id, @RequestBody Map<String, String> body) {
        List<Map<String, Object>> rx = jdbc.queryForList("select patient_id, voided_at from prescriptions where id = ?", id);
        if (rx.isEmpty()) throw ApiException.notFound("La receta");
        if (rx.get(0).get("voided_at") != null) throw ApiException.conflict("YA_ANULADO", "La receta ya está anulada.");
        String reason = body.get("reason") == null ? "" : body.get("reason").trim();
        if (reason.length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación (mínimo 5 caracteres).");
        if (reason.length() > 300) throw ApiException.field("reason", "El motivo admite hasta 300 caracteres.");
        jdbc.update("update prescriptions set voided_at = ?, voided_by = ?, void_reason = ? where id = ?",
                Dates.now(), CurrentUser.usernameOrSystem(), reason, id);
        audit.register("HISTORIA", ((Number) rx.get(0).get("patient_id")).longValue(), "ANULAR_RECETA", reason);
        return Map.of("id", id, "voided", true);
    }

    private static String trim(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (t.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Un texto de la receta es demasiado largo (máx. " + max + ").", Map.of("items", "Texto demasiado largo."));
        return t;
    }
}
