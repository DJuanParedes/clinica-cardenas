package pe.clinicacardenas.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.user.Role;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HU-26: permisos por rol. La matriz vive en la base de datos (role_permissions) y se mantiene en memoria;
 * el filtro JWT asigna los permisos del rol en cada petición, así que un cambio aplica al instante.
 * Los endpoints se protegen con @PreAuthorize("hasAuthority('PERMISO')").
 */
@Service
public class PermissionService {

    /** El administrador conserva siempre todos los permisos para que nadie quede fuera del sistema. */
    public static final Role LOCKED_ROLE = Role.ADMIN;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Map<String, Set<String>> byRole = new ConcurrentHashMap<>();
    private volatile List<Map<String, Object>> catalog = List.of();

    public PermissionService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private synchronized void load() {
        catalog = jdbc.queryForList("select code, group_name as `group`, label from permissions order by sort_order");
        Map<String, Set<String>> m = new HashMap<>();
        for (Role r : Role.values()) m.put(r.name(), new TreeSet<>());
        jdbc.query("select role_code, permission_code from role_permissions",
                rs -> { m.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2)); });
        if (!catalog.isEmpty()) {
            Set<String> all = new TreeSet<>();
            catalog.forEach(c -> all.add((String) c.get("code")));
            m.put(LOCKED_ROLE.name(), all);
        }
        byRole.clear();
        byRole.putAll(m);
    }

    public Set<String> forRole(String role) {
        if (byRole.isEmpty()) load();
        return byRole.getOrDefault(role, Set.of());
    }

    public static boolean has(String permission) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals(permission));
    }

    public Map<String, Object> matrix() {
        if (byRole.isEmpty()) load();
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role r : Role.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.name());
            m.put("label", r.label);
            m.put("locked", r == LOCKED_ROLE);
            m.put("permissions", forRole(r.name()));
            m.put("users", jdbc.queryForObject("select count(*) from users where role_code = ? and active = true", Long.class, r.name()));
            roles.add(m);
        }
        return Map.of("permissions", catalog, "roles", roles);
    }

    @Transactional
    public Map<String, Object> update(String roleCode, Collection<String> permissions) {
        Role role;
        try {
            role = Role.valueOf(roleCode);
        } catch (Exception e) {
            throw ApiException.notFound("El rol");
        }
        if (role == LOCKED_ROLE) {
            throw ApiException.badRequest("ROL_PROTEGIDO", "El administrador conserva todos los permisos para no perder el acceso al sistema.");
        }
        if (byRole.isEmpty()) load();
        Set<String> valid = new HashSet<>();
        catalog.forEach(c -> valid.add((String) c.get("code")));
        Set<String> next = new TreeSet<>(permissions == null ? Set.of() : permissions);
        for (String p : next) {
            if (!valid.contains(p)) throw ApiException.field("permissions", "Permiso desconocido: " + p);
        }
        Set<String> before = forRole(role.name());
        Set<String> added = new TreeSet<>(next);
        added.removeAll(before);
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(next);
        jdbc.update("delete from role_permissions where role_code = ?", role.name());
        for (String p : next) jdbc.update("insert into role_permissions (role_code, permission_code) values (?, ?)", role.name(), p);
        load();
        audit.record("ROL", null, "PERMISOS", role.label + (added.isEmpty() ? "" : " · agregados: " + String.join(", ", added))
                + (removed.isEmpty() ? "" : " · quitados: " + String.join(", ", removed)));
        return matrix();
    }
}
