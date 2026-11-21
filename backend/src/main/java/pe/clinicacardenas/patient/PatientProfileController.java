package pe.clinicacardenas.patient;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.billing.BillingService;

import java.util.List;
import java.util.Map;

/** v2.5: fuente de captación, etiquetas, N° de historia clínica, datos fiscales y familiares. */
@RestController
@RequestMapping("/api/patients")
public class PatientProfileController {

    private final PatientProfileService service;
    private final BillingService billing;

    public PatientProfileController(PatientProfileService service, BillingService billing) {
        this.service = service;
        this.billing = billing;
    }

    @GetMapping("/profile-options")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> options() {
        return Map.of("sources", PatientProfileService.SOURCES, "relationships", PatientProfileService.RELATIONSHIPS,
                "tags", service.allTags());
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> profile(@PathVariable Long id) {
        return service.profile(id);
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody PatientProfileService.ProfileRequest body) {
        return service.updateProfile(id, body);
    }

    /** Consulta de RUC para llenar la razón social (recepción no tiene acceso al módulo de comprobantes). */
    @GetMapping("/ruc/{ruc}")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> ruc(@PathVariable String ruc) {
        return billing.lookupRuc(ruc);
    }

    @PostMapping("/{id}/relations")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> addRelation(@PathVariable Long id, @RequestBody PatientProfileService.RelationRequest body) {
        return service.addRelation(id, body);
    }

    @PutMapping("/{id}/relations/{relationId}")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> updateRelation(@PathVariable Long id, @PathVariable Long relationId,
                                              @RequestBody PatientProfileService.RelationRequest body) {
        return service.updateRelation(id, relationId, body);
    }

    @DeleteMapping("/{id}/relations/{relationId}")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> deleteRelation(@PathVariable Long id, @PathVariable Long relationId) {
        return service.deleteRelation(id, relationId);
    }

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public List<Map<String, Object>> tags() {
        return service.allTags();
    }
}
