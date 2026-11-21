package pe.clinicacardenas.crm;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAuthority('TAREAS')")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "mine") String scope, @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String kind, @RequestParam(required = false) Long assigneeId,
                                    @RequestParam(required = false) Long patientId, @RequestParam(required = false) Long leadId,
                                    @RequestParam(required = false) String q) {
        return service.list(scope, status, kind, assigneeId, patientId, leadId, q);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() { return service.summary(); }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("kinds", TaskService.KINDS, "assignees", service.assignees(), "me", service.currentUserId() == null ? -1 : service.currentUserId());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody TaskService.TaskRequest r) { return service.create(r); }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody TaskService.TaskRequest r) { return service.update(id, r); }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable Long id, @RequestBody(required = false) TaskService.CloseRequest r) { return service.complete(id, r); }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id, @RequestBody(required = false) TaskService.CloseRequest r) { return service.cancel(id, r); }

    @PostMapping("/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable Long id) { return service.reopen(id); }

}
