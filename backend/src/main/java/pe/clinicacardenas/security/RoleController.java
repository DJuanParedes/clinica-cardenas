package pe.clinicacardenas.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HU-26: matriz de roles y permisos.
 * v2.11: solo el administrador entra aquí (rol ADMIN, no un permiso asignable), para que nadie más pueda darse accesos.
 */
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final PermissionService service;

    public RoleController(PermissionService service) {
        this.service = service;
    }

    public record UpdateRequest(List<String> permissions) {}

    @GetMapping
    public Map<String, Object> matrix() {
        return service.matrix();
    }

    @PostMapping("/roles")
    public Map<String, Object> create(@RequestBody PermissionService.NewRole r) {
        return service.createRole(r);
    }

    @PatchMapping("/roles/{code}")
    public Map<String, Object> edit(@PathVariable String code, @RequestBody PermissionService.RoleEdit e) {
        return service.editRole(code, e);
    }

    @DeleteMapping("/roles/{code}")
    public Map<String, Object> delete(@PathVariable String code, @RequestParam(required = false) String reassignTo) {
        return service.deleteRole(code, reassignTo);
    }

    @PatchMapping("/catalog/{code}")
    public Map<String, Object> editPermission(@PathVariable String code, @RequestBody PermissionService.PermissionEdit e) {
        return service.editPermission(code, e);
    }

    @PutMapping("/{role}")
    public Map<String, Object> update(@PathVariable String role, @RequestBody UpdateRequest req) {
        return service.update(role, req.permissions());
    }
}
