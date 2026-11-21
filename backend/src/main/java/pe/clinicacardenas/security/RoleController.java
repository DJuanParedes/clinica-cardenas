package pe.clinicacardenas.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HU-26: matriz de roles y permisos. */
@RestController
@RequestMapping("/api/permissions")
public class RoleController {

    private final PermissionService service;

    public RoleController(PermissionService service) {
        this.service = service;
    }

    public record UpdateRequest(List<String> permissions) {}

    @GetMapping
    @PreAuthorize("hasAuthority('ROLES')")
    public Map<String, Object> matrix() {
        return service.matrix();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES')")
    public Map<String, Object> create(@RequestBody PermissionService.NewRole r) {
        return service.createRole(r);
    }

    @DeleteMapping("/roles/{code}")
    @PreAuthorize("hasAuthority('ROLES')")
    public Map<String, Object> delete(@PathVariable String code) {
        return service.deleteRole(code);
    }

    @PutMapping("/{role}")
    @PreAuthorize("hasAuthority('ROLES')")
    public Map<String, Object> update(@PathVariable String role, @RequestBody UpdateRequest req) {
        return service.update(role, req.permissions());
    }
}
