package pe.clinicacardenas.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.agenda.DentistSchedule;
import pe.clinicacardenas.common.AuditLog;
import pe.clinicacardenas.common.AuditService;

import java.util.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService service;
    private final AuditService audit;

    public UserController(UserService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    public record ActiveRequest(boolean active) {}
    public record PasswordRequest(String newPassword) {}

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public List<Map<String, String>> roles() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Role r : Role.values()) out.add(Map.of("code", r.name(), "label", r.label));
        return out;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIOS')")
    public Map<String, Object> create(@RequestBody UserService.UserRequest req) {
        return service.create(req);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody UserService.UserRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public Map<String, Object> setActive(@PathVariable Long id, @RequestBody ActiveRequest req) {
        return service.setActive(id, req.active());
    }

    @PostMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public Map<String, String> resetPassword(@PathVariable Long id, @RequestBody PasswordRequest req) {
        service.adminResetPassword(id, req.newPassword());
        return Map.of("message", "Contraseña temporal asignada.");
    }

    @PostMapping("/users/{id}/unlock")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public Map<String, Object> unlock(@PathVariable Long id) {
        return service.unlock(id);
    }

    @GetMapping("/users/{id}/audit")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public List<AuditLog> userAudit(@PathVariable Long id) {
        return audit.forEntity("USUARIO", id);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDITORIA')")
    public List<AuditLog> latestAudit() {
        return audit.latest(50);
    }

    @GetMapping("/dentists/{id}/schedules")
    public List<DentistSchedule> schedules(@PathVariable Long id) {
        return service.schedules(id);
    }

    @PutMapping("/dentists/{id}/schedules")
    @PreAuthorize("hasAuthority('USUARIOS')")
    public List<DentistSchedule> replaceSchedules(@PathVariable Long id, @RequestBody List<UserService.ScheduleBlock> blocks) {
        return service.replaceSchedules(id, blocks);
    }
}
