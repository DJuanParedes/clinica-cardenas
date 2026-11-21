package pe.clinicacardenas.crm;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/leads")
@PreAuthorize("hasAuthority('LEADS')")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> board(@RequestParam(required = false) String q, @RequestParam(required = false) String source,
                                     @RequestParam(required = false) Long assigneeId, @RequestParam(defaultValue = "60") int closedDays) {
        return service.board(q, source, assigneeId, closedDays);
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics(@RequestParam(defaultValue = "90") int days) { return service.metrics(days); }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public Map<String, Object> create(@RequestBody LeadService.LeadRequest r) { return service.create(r); }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody LeadService.LeadRequest r) { return service.update(id, r); }

    @PostMapping("/{id}/stage")
    public Map<String, Object> stage(@PathVariable Long id, @RequestBody LeadService.StageRequest r) { return service.stage(id, r); }

    @PostMapping("/{id}/notes")
    public Map<String, Object> note(@PathVariable Long id, @RequestBody LeadService.NoteRequest r) { return service.note(id, r); }

    @PostMapping("/{id}/patient")
    public Map<String, Object> link(@PathVariable Long id, @RequestBody LeadService.LinkRequest r) { return service.linkPatient(id, r); }

    @PutMapping("/settings/whatsapp-auto")
    @PreAuthorize("hasAuthority('LEADS') and hasAuthority('MARKETING')")
    public Map<String, Object> whatsappAuto(@RequestBody Map<String, Boolean> body) { return service.setWhatsappAuto(Boolean.TRUE.equals(body.get("enabled"))); }
}
