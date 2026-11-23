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
    private volatile List<Map<String, Object>> roleRows = List.of();
    private volatile List<Map<String, Object>> catalog = List.of();

    public PermissionService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private synchronized void load() {
        catalog = jdbc.queryForList("select code, group_name as `group`, label from permissions order by sort_order");
        Map<String, Set<String>> m = new HashMap<>();
        for (Role r : Role.values()) m.put(r.name(), new TreeSet<>());
        roleRows = jdbc.queryForList("select code, name, description, base_role as baseRole, custom from roles order by custom, name");
        for (Map<String, Object> r : roleRows) m.putIfAbsent((String) r.get("code"), new TreeSet<>());
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

    /** Nombre visible de un rol (base o personalizado). */
    public String label(String code) {
        if (byRole.isEmpty()) load();
        for (Map<String, Object> r : roleRows) if (code.equals(r.get("code"))) return (String) r.get("name");
        try { return Role.valueOf(code).label; } catch (Exception e) { return code; }
    }

    /** Rol personalizado válido para un usuario de ese puesto (el rol debe derivar del mismo puesto). */
    public boolean isCustomFor(String code, Role base) {
        if (byRole.isEmpty()) load();
        return roleRows.stream().anyMatch(r -> code.equals(r.get("code")) && Boolean.TRUE.equals(asBool(r.get("custom")))
                && base.name().equals(r.get("baseRole")));
    }

    private static Boolean asBool(Object o) {
        return o instanceof Boolean b ? b : o instanceof Number n ? n.intValue() != 0 : Boolean.FALSE;
    }

    public static boolean has(String permission) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals(permission));
    }

    /**
     * v2.10: roles que se pueden asignar a un usuario (los del sistema y los creados por la clínica), con lo que puede
     * hacer cada uno. Lo usa el formulario de usuarios, aunque quien lo llene no administre roles.
     */
    public List<Map<String, Object>> assignable() {
        if (byRole.isEmpty()) load();
        Map<String, String> labels = new HashMap<>();
        Map<String, String> groups = new HashMap<>();
        catalog.forEach(c -> { labels.put((String) c.get("code"), (String) c.get("label")); groups.put((String) c.get("code"), (String) c.get("group")); });
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : roleRows) {
            String code = (String) r.get("code");
            boolean custom = asBool(r.get("custom"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", r.get("name"));
            m.put("description", r.get("description"));
            m.put("custom", custom);
            m.put("baseRole", custom ? r.get("baseRole") : code);
            m.put("baseRoleLabel", label(custom ? (String) r.get("baseRole") : code));
            Set<String> perms = forRole(code);
            m.put("permissionCount", perms.size());
            Map<String, List<String>> byGroup = new TreeMap<>();
            for (String p : perms) byGroup.computeIfAbsent(groups.getOrDefault(p, "Otros"), k -> new ArrayList<>()).add(labels.getOrDefault(p, p));
            m.put("permissionsByGroup", byGroup);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> matrix() {
        if (byRole.isEmpty()) load();
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Map<String, Object> r : roleRows) {
            String code = (String) r.get("code");
            boolean custom = asBool(r.get("custom"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", r.get("name"));
            m.put("description", r.get("description"));
            m.put("custom", custom);
            m.put("baseRole", r.get("baseRole"));
            m.put("baseRoleLabel", r.get("baseRole") == null ? null : label((String) r.get("baseRole")));
            m.put("locked", LOCKED_ROLE.name().equals(code));
            m.put("permissions", forRole(code));
            m.put("users", custom
                    ? jdbc.queryForObject("select count(*) from users where access_role_code = ? and active = true", Long.class, code)
                    : jdbc.queryForObject("select count(*) from users where role_code = ? and access_role_code is null and active = true", Long.class, code));
            roles.add(m);
        }
        return Map.of("permissions", catalog, "roles", roles);
    }

    public record NewRole(String name, String description, String baseRole) {}

    /** Crea un rol personalizado a partir de un puesto base, copiando sus permisos para luego ajustarlos. */
    @Transactional
    public Map<String, Object> createRole(NewRole r) {
        if (byRole.isEmpty()) load();
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 3 || name.length() > 60) throw ApiException.field("name", "Ponle un nombre de 3 a 60 caracteres.");
        Role base;
        try { base = Role.valueOf(r.baseRole()); } catch (Exception e) { throw ApiException.field("baseRole", "Selecciona el puesto base."); }
        if (base == LOCKED_ROLE) throw ApiException.field("baseRole", "No se crean variantes del administrador.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from roles where lower(name) = lower(?)", Boolean.class, name))) {
            throw ApiException.field("name", "Ya existe un rol con ese nombre.");
        }
        String slug = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        String code = ("R_" + slug);
        if (code.length() > 17) code = code.substring(0, 17);
        String unique = code;
        for (int k = 2; Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from roles where code = ?", Boolean.class, unique)); k++) unique = code + k;
        jdbc.update("insert into roles (code, name, description, base_role, custom, created_at) values (?, ?, ?, ?, true, now())",
                unique, name, r.description() == null || r.description().isBlank() ? null : r.description().trim(), base.name());
        for (String p : forRole(base.name())) jdbc.update("insert into role_permissions (role_code, permission_code) values (?, ?)", unique, p);
        load();
        audit.record("ROL", null, "CREAR", name + " (basado en " + base.label + ")");
        return matrix();
    }

    @Transactional
    public Map<String, Object> deleteRole(String code) {
        if (byRole.isEmpty()) load();
        Map<String, Object> row = roleRows.stream().filter(x -> code.equals(x.get("code"))).findFirst().orElseThrow(() -> ApiException.notFound("El rol"));
        if (!asBool(row.get("custom"))) throw ApiException.badRequest("ROL_BASE", "Los roles base del sistema no se eliminan; solo puedes cambiar sus permisos.");
        Long n = jdbc.queryForObject("select count(*) from users where access_role_code = ?", Long.class, code);
        if (n != null && n > 0) throw ApiException.conflict("ROL_EN_USO", "Hay " + n + " usuario(s) con este rol. Cámbialos de rol antes de eliminarlo.");
        jdbc.update("delete from role_permissions where role_code = ?", code);
        jdbc.update("delete from roles where code = ?", code);
        load();
        audit.record("ROL", null, "ELIMINAR", (String) row.get("name"));
        return matrix();
    }

    @Transactional
    public Map<String, Object> update(String roleCode, Collection<String> permissions) {
        if (byRole.isEmpty()) load();
        if (roleRows.stream().noneMatch(x -> roleCode.equals(x.get("code")))) throw ApiException.notFound("El rol");
        if (LOCKED_ROLE.name().equals(roleCode)) {
            throw ApiException.badRequest("ROL_PROTEGIDO", "El administrador conserva todos los permisos para no perder el acceso al sistema.");
        }
        Set<String> valid = new HashSet<>();
        catalog.forEach(c -> valid.add((String) c.get("code")));
        Set<String> next = new TreeSet<>(permissions == null ? Set.of() : permissions);
        for (String p : next) {
            if (!valid.contains(p)) throw ApiException.field("permissions", "Permiso desconocido: " + p);
        }
        Set<String> before = forRole(roleCode);
        Set<String> added = new TreeSet<>(next);
        added.removeAll(before);
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(next);
        jdbc.update("delete from role_permissions where role_code = ?", roleCode);
        for (String p : next) jdbc.update("insert into role_permissions (role_code, permission_code) values (?, ?)", roleCode, p);
        load();
        audit.record("ROL", null, "PERMISOS", label(roleCode) + (added.isEmpty() ? "" : " · agregados: " + String.join(", ", added))
                + (removed.isEmpty() ? "" : " · quitados: " + String.join(", ", removed)));
        return matrix();
    }
}
