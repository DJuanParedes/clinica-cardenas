package pe.clinicacardenas.lab;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v2.5: laboratorios y órdenes de laboratorio. Caja también los ve para poder pagarlos. */
@RestController
@RequestMapping("/api")
public class LabController {

    private static final String VIEW = "hasAnyAuthority('LABORATORIO','CAJA_EGRESOS')";
    private final LabService service;

    public LabController(LabService service) {
        this.service = service;
    }

    @GetMapping("/lab-options")
    @PreAuthorize(VIEW)
    public Map<String, Object> options() {
        return Map.of("workTypes", LabService.WORK_TYPES.entrySet().stream().map(e -> Map.of("code", e.getKey(), "label", e.getValue())).toList(),
                "laboratories", service.labs(false));
    }

    @GetMapping("/laboratories")
    @PreAuthorize(VIEW)
    public List<Map<String, Object>> labs(@RequestParam(defaultValue = "false") boolean all) {
        return service.labs(all);
    }

    @PostMapping("/laboratories")
    @PreAuthorize("hasAuthority('LABORATORIO')")
    public Map<String, Object> createLab(@RequestBody LabService.LabRequest body) {
        return service.saveLab(null, body);
    }

    @PutMapping("/laboratories/{id}")
    @PreAuthorize("hasAuthority('LABORATORIO')")
    public Map<String, Object> updateLab(@PathVariable Long id, @RequestBody LabService.LabRequest body) {
        return service.saveLab(id, body);
    }

    @GetMapping("/lab-orders")
    @PreAuthorize(VIEW)
    public Map<String, Object> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long laboratoryId,
                                    @RequestParam(required = false) Long patientId, @RequestParam(defaultValue = "false") boolean overdue,
                                    @RequestParam(defaultValue = "false") boolean unpaid, @RequestParam(required = false) String q) {
        return service.list(status, laboratoryId, patientId, overdue, unpaid, q);
    }

    @GetMapping("/lab-orders/{id}")
    @PreAuthorize(VIEW)
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/lab-orders")
    @PreAuthorize("hasAuthority('LABORATORIO')")
    public Map<String, Object> create(@RequestBody LabService.OrderRequest body) {
        return service.create(body);
    }

    @PutMapping("/lab-orders/{id}")
    @PreAuthorize("hasAuthority('LABORATORIO')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody LabService.OrderRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/lab-orders/{id}/status")
    @PreAuthorize("hasAuthority('LABORATORIO')")
    public Map<String, Object> status(@PathVariable Long id, @RequestBody LabService.StatusRequest body) {
        return service.changeStatus(id, body);
    }

    @PostMapping("/lab-orders/{id}/payments")
    @PreAuthorize("hasAuthority('CAJA_EGRESOS')")
    public Map<String, Object> pay(@PathVariable Long id, @RequestBody LabService.PaymentRequest body) {
        return service.pay(id, body);
    }
}
