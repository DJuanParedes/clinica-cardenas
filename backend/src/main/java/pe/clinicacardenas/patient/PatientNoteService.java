package pe.clinicacardenas.patient;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;
import pe.clinicacardenas.security.PermissionService;

import java.util.*;

/**
 * v2.8: notas fijas de la ficha ("prefiere citas en la tarde", "viene con su hija", "pide factura") y la franja con
 * las alergias y alertas médicas. El detalle médico solo lo ve el personal clínico; el resto ve que hay una alerta.
 */
@RestController
@RequestMapping("/api/patients/{patientId}")
public class PatientNoteService {
    private static final String PACIENTE = "PACIENTE";


    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PatientNoteService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record NoteRequest(String text, String tone, Boolean pinned) {}

    @GetMapping("/flags")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    @Transactional(readOnly = true)
    public Map<String, Object> flags(@PathVariable Long patientId) {
        exists(patientId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("notes", jdbc.queryForList("""
                select id, text, tone, pinned, created_by as createdBy, created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt
                  from patient_notes where patient_id = ? and deleted_at is null order by pinned desc, created_at desc
                """, patientId));
        List<Map<String, Object>> h = jdbc.queryForList("select allergies, current_medication from clinical_histories where patient_id = ?", patientId);
        String allergies = h.isEmpty() ? null : blank((String) h.get(0).get("allergies"));
        String medication = h.isEmpty() ? null : blank((String) h.get(0).get("current_medication"));
        boolean clinical = PermissionService.has("HISTORIA_CLINICA");
        boolean hasMedical = allergies != null || medication != null;
        m.put("hasMedicalAlert", hasMedical);
        if (clinical) {
            m.put("allergies", allergies);
            m.put("medication", medication);
        }
        return m;
    }

    @PostMapping("/notes")
    @PreAuthorize("hasAnyAuthority('PACIENTES_EDITAR','HISTORIA_CLINICA')")
    @Transactional
    public Map<String, Object> create(@PathVariable Long patientId, @RequestBody NoteRequest r) {
        exists(patientId);
        Clean c = clean(r);
        if (jdbc.queryForObject("select count(*) from patient_notes where patient_id = ? and deleted_at is null", Long.class, patientId) >= 20)
            throw ApiException.badRequest("DEMASIADAS_NOTAS", "Máximo 20 notas por paciente: borra las que ya no sirven.");
        jdbc.update("insert into patient_notes (patient_id, text, tone, pinned, created_by, created_at) values (?, ?, ?, ?, ?, ?)",
                patientId, c.text, c.tone, c.pinned, CurrentUser.usernameOrSystem(), Dates.now());
        audit.register(PACIENTE, patientId, "NOTA", "Agregó una nota: " + c.text);
        return flags(patientId);
    }

    @PutMapping("/notes/{id}")
    @PreAuthorize("hasAnyAuthority('PACIENTES_EDITAR','HISTORIA_CLINICA')")
    @Transactional
    public Map<String, Object> update(@PathVariable Long patientId, @PathVariable Long id, @RequestBody NoteRequest r) {
        Clean c = clean(r);
        if (jdbc.update("update patient_notes set text = ?, tone = ?, pinned = ?, updated_by = ?, updated_at = ? where id = ? and patient_id = ? and deleted_at is null",
                c.text, c.tone, c.pinned, CurrentUser.usernameOrSystem(), Dates.now(), id, patientId) == 0) throw ApiException.notFound("La nota");
        audit.register(PACIENTE, patientId, "NOTA", "Editó una nota: " + c.text);
        return flags(patientId);
    }

    @DeleteMapping("/notes/{id}")
    @PreAuthorize("hasAuthority('ELIMINAR_REGISTROS')")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long patientId, @PathVariable Long id) {
        if (jdbc.update("update patient_notes set deleted_at = ?, deleted_by = ? where id = ? and patient_id = ? and deleted_at is null",
                Dates.now(), CurrentUser.usernameOrSystem(), id, patientId) == 0) throw ApiException.notFound("La nota");
        audit.register(PACIENTE, patientId, "NOTA", "Borró una nota");
        return flags(patientId);
    }

    private record Clean(String text, String tone, boolean pinned) {}

    private static Clean clean(NoteRequest r) {
        String t = r.text() == null ? "" : r.text().trim().replaceAll("\\s+", " ");
        if (t.length() < 2 || t.length() > 500) throw ApiException.field("text", "Escribe la nota (2 a 500 caracteres).");
        String tone = r.tone() == null || r.tone().isBlank() ? "AMARILLO" : r.tone().trim().toUpperCase();
        if (!Set.of("AMARILLO", "ROJO", "AZUL").contains(tone)) throw ApiException.field("tone", "Color no válido.");
        return new Clean(t, tone, r.pinned() == null || r.pinned());
    }

    private void exists(Long id) {
        if (jdbc.queryForObject("select count(*) from patients where id = ?", Long.class, id) == 0) throw ApiException.notFound("El paciente");
    }

    private static String blank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || t.equalsIgnoreCase("ninguna") || t.equalsIgnoreCase("ninguno") || t.equalsIgnoreCase("no refiere") || t.equals("-") ? null : t;
    }
}
