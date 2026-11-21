package pe.clinicacardenas.clinical;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Catálogo de procedimientos (variantes) y promociones. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final PromotionService service;

    public CatalogController(PromotionService service) {
        this.service = service;
    }

    @GetMapping("/procedures")
    public List<Procedure> procedures(@RequestParam(defaultValue = "false") boolean all) {
        return service.catalog(all && pe.clinicacardenas.security.PermissionService.has("PROMOCIONES"));
    }

    @PostMapping("/procedures")
    @PreAuthorize("hasAuthority('PROMOCIONES')")
    public Procedure create(@RequestBody PromotionService.ProcedureRequest r) { return service.saveProcedure(null, r); }

    @PutMapping("/procedures/{id}")
    @PreAuthorize("hasAuthority('PROMOCIONES')")
    public Procedure update(@PathVariable Long id, @RequestBody PromotionService.ProcedureRequest r) { return service.saveProcedure(id, r); }

    @GetMapping("/promotions")
    public List<Map<String, Object>> promotions() { return service.promotions(); }

    @PostMapping("/promotions")
    @PreAuthorize("hasAuthority('PROMOCIONES')")
    public List<Map<String, Object>> createPromo(@RequestBody PromotionService.PromoRequest r) { return service.savePromotion(null, r); }

    @PutMapping("/promotions/{id}")
    @PreAuthorize("hasAuthority('PROMOCIONES')")
    public List<Map<String, Object>> updatePromo(@PathVariable Long id, @RequestBody PromotionService.PromoRequest r) { return service.savePromotion(id, r); }
}
