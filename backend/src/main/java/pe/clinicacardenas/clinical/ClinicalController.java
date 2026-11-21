package pe.clinicacardenas.clinical;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HU-04, HU-05 y HU-06. El acceso a la historia clínica se restringe por rol en el servidor. */
@RestController
@RequestMapping("/api")
public class ClinicalController {

    private static final String CLINICAL = "hasAuthority('HISTORIA_CLINICA')";
    private static final String PLAN = "hasAuthority('PLAN_TRATAMIENTO')";

    private final ClinicalService service;

    public ClinicalController(ClinicalService service) {
        this.service = service;
    }

    @GetMapping("/procedures")
    public List<Procedure> procedures() {
        return service.procedures();
    }

    @GetMapping("/patients/{id}/history")
    @PreAuthorize(CLINICAL)
    public Map<String, Object> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PutMapping("/patients/{id}/history")
    @PreAuthorize(CLINICAL)
    public ClinicalHistory saveHistory(@PathVariable Long id, @RequestBody ClinicalService.HistoryRequest r) {
        return service.saveHistory(id, r);
    }

    @PostMapping("/patients/{id}/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CLINICAL)
    public Diagnosis diagnosis(@PathVariable Long id, @RequestBody ClinicalService.DiagnosisRequest r) {
        return service.addDiagnosis(id, r);
    }

    @GetMapping("/patients/{id}/odontogram")
    @PreAuthorize(CLINICAL)
    public Map<String, Object> odontogram(@PathVariable Long id) {
        return service.odontogram(id);
    }

    @PutMapping("/patients/{id}/odontogram")
    @PreAuthorize(CLINICAL)
    public Map<String, Object> saveOdontogram(@PathVariable Long id, @RequestBody ClinicalService.OdontogramRequest r) {
        return service.saveOdontogram(id, r);
    }

    @GetMapping("/patients/{id}/plans")
    @PreAuthorize("hasAnyAuthority('PLAN_TRATAMIENTO','HISTORIA_CLINICA','CAJA_COBRAR')")
    public List<Map<String, Object>> plans(@PathVariable Long id) {
        return service.plans(id);
    }

    @PostMapping("/patients/{id}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PLAN)
    public Map<String, Object> createPlan(@PathVariable Long id, @RequestBody ClinicalService.PlanRequest r) {
        return service.createPlan(id, r);
    }

    @PostMapping("/plans/{planId}/items")
    @PreAuthorize(PLAN)
    public Map<String, Object> addItem(@PathVariable Long planId, @RequestBody ClinicalService.PlanItemRequest r) {
        return service.addItem(planId, r);
    }

    @PatchMapping("/plan-items/{itemId}")
    @PreAuthorize(PLAN)
    public Map<String, Object> itemStatus(@PathVariable Long itemId, @RequestBody ClinicalService.ItemStatusRequest r) {
        return service.setItemStatus(itemId, r.status());
    }

    @PostMapping("/plans/{planId}/evolutions")
    @PreAuthorize(PLAN)
    public Map<String, Object> evolution(@PathVariable Long planId, @RequestBody ClinicalService.EvolutionRequest r) {
        return service.addEvolution(planId, r);
    }

    @PostMapping("/plans/{planId}/cancel")
    @PreAuthorize(PLAN)
    public Map<String, Object> cancel(@PathVariable Long planId) {
        return service.cancelPlan(planId);
    }
}
