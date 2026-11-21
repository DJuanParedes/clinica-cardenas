package pe.clinicacardenas.clinical;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v2.5: anamnesis por plantillas y signos vitales (datos de salud: solo el personal asistencial). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
public class AnamnesisController {

    private final AnamnesisService service;

    public AnamnesisController(AnamnesisService service) {
        this.service = service;
    }

    @GetMapping("/anamnesis/templates")
    public List<Map<String, Object>> templates(@RequestParam(defaultValue = "false") boolean all) {
        return service.templates(all);
    }

    @PostMapping("/anamnesis/templates")
    public Map<String, Object> create(@RequestBody AnamnesisService.TemplateRequest body) {
        return service.saveTemplate(null, body);
    }

    @PutMapping("/anamnesis/templates/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody AnamnesisService.TemplateRequest body) {
        return service.saveTemplate(id, body);
    }

    @GetMapping("/patients/{id}/anamnesis")
    public Map<String, Object> records(@PathVariable Long id) {
        return Map.of("records", service.records(id), "alerts", service.alerts(id));
    }

    @PostMapping("/patients/{id}/anamnesis")
    public Map<String, Object> add(@PathVariable Long id, @RequestBody AnamnesisService.RecordRequest body) {
        return service.saveRecord(id, null, body);
    }

    @PutMapping("/patients/{id}/anamnesis/{recordId}")
    public Map<String, Object> edit(@PathVariable Long id, @PathVariable Long recordId, @RequestBody AnamnesisService.RecordRequest body) {
        return service.saveRecord(id, recordId, body);
    }

    @GetMapping("/patients/{id}/vitals")
    public List<Map<String, Object>> vitals(@PathVariable Long id) {
        return service.vitals(id);
    }

    @PostMapping("/patients/{id}/vitals")
    public List<Map<String, Object>> addVitals(@PathVariable Long id, @RequestBody AnamnesisService.VitalsRequest body) {
        return service.addVitals(id, body);
    }

    @DeleteMapping("/patients/{id}/vitals/{vitalId}")
    public List<Map<String, Object>> deleteVitals(@PathVariable Long id, @PathVariable Long vitalId) {
        return service.deleteVitals(id, vitalId);
    }
}
