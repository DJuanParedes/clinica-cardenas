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
    private static final String ROL_EN_USO = "ROL_EN_USO";
    private static final String BASEROLE = "baseRole";
    private static final String CUSTOM = "custom";
    private static final String DESCRIPTION = "description";
    private static final String GROUP = "group";
    private static final String HIDDEN = "hidden";
    private static final String LABEL = "label";
    private static final String PERMISSIONS = "permissions";


    /** El administrador conserva siempre todos los permisos para que nadie quede fuera del sistema. */
    public static final Role LOCKED_ROLE = Role.ADMIN;
    /** v2.11: permisos que solo puede tener el administrador (nadie más puede tocar roles y permisos). */
    public static final Set<String> ADMIN_ONLY = Set.of("ROLES");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Map<String, Set<String>> byRole = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicReference<List<Map<String, Object>>> roleRows = new java.util.concurrent.atomic.AtomicReference<>(List.of());
    private final java.util.concurrent.atomic.AtomicReference<List<Map<String, Object>>> catalog = new java.util.concurrent.atomic.AtomicReference<>(List.of());

    public PermissionService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private synchronized void load() {
        catalog.set(jdbc.queryForList("select code, group_name as `group`, label, description from permissions order by sort_order"));
        Map<String, Set<String>> m = new HashMap<>();
        for (Role r : Role.values()) m.put(r.name(), new TreeSet<>());
        roleRows.set(jdbc.queryForList("select code, name, description, base_role as baseRole, custom, hidden from roles order by custom, name"));
        for (Map<String, Object> r : roleRows.get()) m.putIfAbsent((String) r.get("code"), new TreeSet<>());
        jdbc.query("select role_code, permission_code from role_permissions",
                rs -> { m.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2)); });
        if (!catalog.get().isEmpty()) {
            Set<String> all = new TreeSet<>();
            catalog.get().forEach(c -> all.add((String) c.get("code")));
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
        for (Map<String, Object> r : roleRows.get()) if (code.equals(r.get("code"))) return (String) r.get("name");
        try { return Role.valueOf(code).label; } catch (Exception e) { return code; }
    }

    /** Rol personalizado válido para un usuario de ese puesto (el rol debe derivar del mismo puesto). */
    public boolean isCustomFor(String code, Role base) {
        if (byRole.isEmpty()) load();
        return roleRows.get().stream().anyMatch(r -> code.equals(r.get("code")) && Boolean.TRUE.equals(asBool(r.get(CUSTOM)))
                && base.name().equals(r.get(BASEROLE)));
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
        catalog.get().forEach(c -> { labels.put((String) c.get("code"), (String) c.get(LABEL)); groups.put((String) c.get("code"), (String) c.get(GROUP)); });
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : roleRows.get()) {
            String code = (String) r.get("code");
            boolean custom = asBool(r.get(CUSTOM));
            if (asBool(r.get(HIDDEN))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put(LABEL, r.get("name"));
            m.put(DESCRIPTION, r.get(DESCRIPTION));
            m.put(CUSTOM, custom);
            m.put(BASEROLE, custom ? r.get(BASEROLE) : code);
            m.put("baseRoleLabel", label(custom ? (String) r.get(BASEROLE) : code));
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
        for (Map<String, Object> r : roleRows.get()) {
            String code = (String) r.get("code");
            boolean custom = asBool(r.get(CUSTOM));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put(LABEL, r.get("name"));
            m.put(DESCRIPTION, r.get(DESCRIPTION));
            m.put(CUSTOM, custom);
            m.put(BASEROLE, r.get(BASEROLE));
            m.put("baseRoleLabel", r.get(BASEROLE) == null ? null : label((String) r.get(BASEROLE)));
            m.put("locked", LOCKED_ROLE.name().equals(code));
            m.put(HIDDEN, asBool(r.get(HIDDEN)));
            m.put(PERMISSIONS, forRole(code));
            m.put("users", custom
                    ? jdbc.queryForObject("select count(*) from users where access_role_code = ? and active = true", Long.class, code)
                    : jdbc.queryForObject("select count(*) from users where role_code = ? and access_role_code is null and active = true", Long.class, code));
            roles.add(m);
        }
        return Map.of(PERMISSIONS, catalog.get(), "roles", roles, "adminOnly", ADMIN_ONLY);
    }

    /** copyFrom: código de un rol existente para copiar sus permisos, "VACIO" para empezar sin permisos, o nada para copiar el puesto base. */
    public record NewRole(String name, String description, String baseRole, String copyFrom) {}

    /** Crea un rol personalizado a partir de un puesto base, copiando sus permisos para luego ajustarlos. */
    @Transactional
    public Map<String, Object> createRole(NewRole r) {
        if (byRole.isEmpty()) load();
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 3 || name.length() > 60) throw ApiException.field("name", "Ponle un nombre de 3 a 60 caracteres.");
        Role base;
        try { base = Role.valueOf(r.baseRole()); } catch (Exception e) { throw ApiException.field(BASEROLE, "Selecciona el puesto base."); }
        if (base == LOCKED_ROLE) throw ApiException.field(BASEROLE, "No se crean variantes del administrador.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from roles where lower(name) = lower(?)", Boolean.class, name))) {
            throw ApiException.field("name", "Ya existe un rol con ese nombre.");
        }
        String slug = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_)|(_$)", "");
        String code = ("R_" + slug);
        if (code.length() > 17) code = code.substring(0, 17);
        String unique = code;
        for (int k = 2; Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from roles where code = ?", Boolean.class, unique)); k++) unique = code + k;
        jdbc.update("insert into roles (code, name, description, base_role, custom, created_at) values (?, ?, ?, ?, true, now())",
                unique, name, r.description() == null || r.description().isBlank() ? null : r.description().trim(), base.name());
        String from = r.copyFrom() == null || r.copyFrom().isBlank() ? base.name() : r.copyFrom();
        Set<String> seed = "VACIO".equals(from) ? Set.of() : new TreeSet<>(forRole(from));
        if (!"VACIO".equals(from) && roleRows.get().stream().noneMatch(x -> from.equals(x.get("code")))) throw ApiException.field("copyFrom", "El rol a copiar no existe.");
        for (String p : seed) if (!ADMIN_ONLY.contains(p)) jdbc.update("insert into role_permissions (role_code, permission_code) values (?, ?)", unique, p);
        load();
        audit.record("ROL", null, "CREAR", name + " (basado en " + base.label + ")");
        return matrix();
    }

    private Map<String, Object> row(String code) {
        if (byRole.isEmpty()) load();
        return roleRows.get().stream().filter(x -> code.equals(x.get("code"))).findFirst().orElseThrow(() -> ApiException.notFound("El rol"));
    }

    private long usersWith(String code, boolean custom) {
        Long n = custom
                ? jdbc.queryForObject("select count(*) from users where access_role_code = ?", Long.class, code)
                : jdbc.queryForObject("select count(*) from users where role_code = ? and access_role_code is null", Long.class, code);
        return n == null ? 0 : n;
    }

    public record RoleEdit(String name, String description, Boolean hidden) {}

    /** v2.11: cambia nombre, descripción o visibilidad de cualquier rol (también los del sistema). */
    @Transactional
    public Map<String, Object> editRole(String code, RoleEdit e) {
        Map<String, Object> row = row(code);
        boolean custom = asBool(row.get(CUSTOM));
        String name = e.name() == null ? (String) row.get("name") : e.name().trim();
        if (name.length() < 3 || name.length() > 60) throw ApiException.field("name", "Ponle un nombre de 3 a 60 caracteres.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from roles where lower(name) = lower(?) and code <> ?", Boolean.class, name, code))) {
            throw ApiException.field("name", "Ya existe un rol con ese nombre.");
        }
        String desc = e.description() == null ? (String) row.get(DESCRIPTION) : (e.description().isBlank() ? null : e.description().trim());
        boolean hidden = e.hidden() == null ? asBool(row.get(HIDDEN)) : e.hidden();
        if (hidden && !asBool(row.get(HIDDEN))) {
            if (LOCKED_ROLE.name().equals(code)) throw ApiException.badRequest("ROL_PROTEGIDO", "El rol Administrador no se puede ocultar.");
            long n = usersWith(code, custom);
            if (n > 0) throw ApiException.conflict(ROL_EN_USO, "Hay " + n + " usuario(s) con este rol. Cámbialos de rol antes de ocultarlo.");
            if (!custom) {
                Long derived = jdbc.queryForObject("select count(*) from roles where base_role = ? and hidden = false", Long.class, code);
                if (derived != null && derived > 0) throw ApiException.conflict(ROL_EN_USO, "Hay roles creados a partir de este puesto. Ocúltalos o elimínalos primero.");
            }
        }
        jdbc.update("update roles set name = ?, description = ?, hidden = ? where code = ?", name, desc, hidden, code);
        load();
        audit.record("ROL", null, "EDITAR", row.get("name") + (name.equals(row.get("name")) ? "" : " → " + name) + (hidden ? " · oculto" : ""));
        return matrix();
    }

    /** Elimina un rol creado por la clínica. Si tiene usuarios, se pasan antes al rol indicado en reassignTo. */
    @Transactional
    public Map<String, Object> deleteRole(String code, String reassignTo) {
        Map<String, Object> row = row(code);
        if (!asBool(row.get(CUSTOM))) throw ApiException.badRequest("ROL_BASE", "Los roles del sistema no se eliminan porque el programa depende de ellos; puedes renombrarlos u ocultarlos.");
        long n = usersWith(code, true);
        if (n > 0) {
            if (reassignTo == null || reassignTo.isBlank() || reassignTo.equals(code)) {
                throw ApiException.conflict(ROL_EN_USO, "Hay " + n + " usuario(s) con este rol. Elige a qué rol pasarlos.");
            }
            Map<String, Object> target = row(reassignTo);
            if (asBool(target.get(HIDDEN))) throw ApiException.field("reassignTo", "Ese rol está oculto.");
            if (asBool(target.get(CUSTOM))) {
                jdbc.update("update users set role_code = ?, access_role_code = ? where access_role_code = ?", target.get(BASEROLE), reassignTo, code);
            } else {
                if (LOCKED_ROLE.name().equals(reassignTo)) throw ApiException.field("reassignTo", "Para dar acceso de administrador, cámbialo desde Usuarios y accesos.");
                jdbc.update("update users set role_code = ?, access_role_code = null where access_role_code = ?", reassignTo, code);
            }
        }
        jdbc.update("delete from role_permissions where role_code = ?", code);
        jdbc.update("delete from roles where code = ?", code);
        load();
        audit.record("ROL", null, "ELIMINAR", row.get("name") + (n > 0 ? " · " + n + " usuario(s) pasados a " + label(reassignTo) : ""));
        return matrix();
    }

    @Transactional
    public Map<String, Object> update(String roleCode, Collection<String> permissions) {
        if (byRole.isEmpty()) load();
        if (roleRows.get().stream().noneMatch(x -> roleCode.equals(x.get("code")))) throw ApiException.notFound("El rol");
        if (LOCKED_ROLE.name().equals(roleCode)) {
            throw ApiException.badRequest("ROL_PROTEGIDO", "El administrador conserva todos los permisos para no perder el acceso al sistema.");
        }
        Set<String> valid = new HashSet<>();
        catalog.get().forEach(c -> valid.add((String) c.get("code")));
        Set<String> next = new TreeSet<>(permissions == null ? Set.of() : permissions);
        for (String p : next) {
            if (!valid.contains(p)) throw ApiException.field(PERMISSIONS, "Permiso desconocido: " + p);
            if (ADMIN_ONLY.contains(p)) throw ApiException.field(PERMISSIONS, "Administrar roles y permisos es exclusivo del administrador.");
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

    public record PermissionEdit(String label, String group, String description) {}

    /**
     * v2.11: cambia cómo se llama, en qué grupo aparece y la descripción de un permiso. El código no cambia, porque
     * el programa lo usa para decidir qué se puede hacer; por la misma razón no se crean ni borran permisos desde aquí.
     */
    @Transactional
    public Map<String, Object> editPermission(String code, PermissionEdit e) {
        if (byRole.isEmpty()) load();
        Map<String, Object> p = catalog.get().stream().filter(c -> code.equals(c.get("code"))).findFirst().orElseThrow(() -> ApiException.notFound("El permiso"));
        String label = e.label() == null ? (String) p.get(LABEL) : e.label().trim();
        String group = e.group() == null ? (String) p.get(GROUP) : e.group().trim();
        if (label.length() < 3 || label.length() > 120) throw ApiException.field(LABEL, "El nombre debe tener de 3 a 120 caracteres.");
        if (group.length() < 3 || group.length() > 40) throw ApiException.field(GROUP, "El grupo debe tener de 3 a 40 caracteres.");
        String desc = e.description() == null ? (String) p.get(DESCRIPTION) : (e.description().isBlank() ? null : e.description().trim());
        jdbc.update("update permissions set label = ?, group_name = ?, description = ? where code = ?", label, group, desc, code);
        load();
        audit.record("PERMISO", null, "EDITAR", code + ": " + label + " (" + group + ")");
        return matrix();
    }
}
