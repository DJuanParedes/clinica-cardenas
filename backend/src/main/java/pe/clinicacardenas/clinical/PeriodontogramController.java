package pe.clinicacardenas.clinical;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v2.5: periodontograma por paciente (datos de salud: solo el personal asistencial). */
@RestController
@RequestMapping("/api/patients/{patientId}/periodontograms")
@PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
public class PeriodontogramController {

    private final PeriodontogramService service;

    public PeriodontogramController(PeriodontogramService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable Long patientId) {
        return service.list(patientId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long patientId, @PathVariable Long id) {
        return service.get(patientId, id);
    }

    @PostMapping
    public Map<String, Object> create(@PathVariable Long patientId, @RequestBody PeriodontogramService.ExamRequest body) {
        return service.save(patientId, null, body);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long patientId, @PathVariable Long id, @RequestBody PeriodontogramService.ExamRequest body) {
        return service.save(patientId, id, body);
    }
}
