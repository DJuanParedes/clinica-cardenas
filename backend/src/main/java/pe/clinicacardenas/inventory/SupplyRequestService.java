package pe.clinicacardenas.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;
import pe.clinicacardenas.security.PermissionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * El odontólogo no maneja el inventario: pide lo que necesita y recepción (o quien tenga el permiso INVENTARIO)
 * lo entrega, descontándolo del stock con su lote; o lo rechaza indicando el motivo. Queda trazabilidad de quién
 * pidió, quién entregó y cuándo.
 */
@Service
public class SupplyRequestService {
    private static final String QUANTITY = "quantity";


    private final JdbcTemplate jdbc;
    private final InventoryItemRepository items;
    private final InventoryService inventory;
    private final AuditService audit;

    public SupplyRequestService(JdbcTemplate jdbc, InventoryItemRepository items, InventoryService inventory, AuditService audit) {
        this.jdbc = jdbc;
        this.items = items;
        this.inventory = inventory;
        this.audit = audit;
    }

    public record NewRequest(Long itemId, String itemText, BigDecimal quantity, String urgency, String note) {}

    public record Answer(String action, BigDecimal quantity, String answer) {}

    /** Catálogo mínimo para pedir: nombre, unidad y si hay stock (sin costos ni proveedores). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalog() {
        return jdbc.queryForList("select id, name, unit, category, (stock > 0) as available from inventory_items where active = true order by name");
    }

    @Transactional
    public Map<String, Object> create(NewRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String text = r.itemText() == null ? null : r.itemText().trim();
        if (r.itemId() == null && (text == null || text.length() < 3)) e.put("itemId", "Elige el material o escribe qué necesitas.");
        if (r.itemId() != null && items.findById(r.itemId()).isEmpty()) e.put("itemId", "El material no existe.");
        if (r.quantity() == null || r.quantity().signum() <= 0) e.put(QUANTITY, "Indica la cantidad.");
        else if (r.quantity().compareTo(new BigDecimal("1000")) > 0) e.put(QUANTITY, "Cantidad demasiado alta para una solicitud.");
        String urgency = "URGENTE".equalsIgnoreCase(r.urgency()) ? "URGENTE" : "NORMAL";
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la solicitud.", e);
        jdbc.update("""
                insert into supply_requests (item_id, item_text, quantity, urgency, note, status, requested_by, requested_at)
                values (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)""",
                r.itemId(), r.itemId() == null ? text : null, r.quantity().setScale(2, RoundingMode.HALF_UP), urgency,
                r.note() == null || r.note().isBlank() ? null : r.note().trim(), CurrentUser.usernameOrSystem(), Dates.now());
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        audit.register("SOLICITUD_INSUMO", id, "CREAR", (r.itemId() == null ? text : items.findById(r.itemId()).map(x -> x.name).orElse(text)) + " × " + r.quantity());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, boolean mine) {
        StringBuilder sql = new StringBuilder("""
                select r.id, r.item_id as itemId, coalesce(i.name, r.item_text) as item, i.unit, r.quantity, r.urgency, r.note, r.status,
                       r.requested_by as requestedBy, u.full_name as requestedByName, r.requested_at as requestedAt,
                       r.answered_by as answeredBy, r.answered_at as answeredAt, r.answer, i.stock
                  from supply_requests r left join inventory_items i on i.id = r.item_id
                  left join users u on lower(u.username) = lower(r.requested_by) where 1 = 1""");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) { sql.append(" and r.status = ?"); args.add(status.trim().toUpperCase()); }
        if (mine) { sql.append(" and lower(r.requested_by) = lower(?)"); args.add(CurrentUser.usernameOrSystem()); }
        sql.append(" order by r.status = 'PENDIENTE' desc, r.urgency = 'URGENTE' desc, r.requested_at desc limit 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private Map<String, Object> get(Long id) {
        return jdbc.queryForList("""
                select r.id, coalesce(i.name, r.item_text) as item, i.unit, r.quantity, r.urgency, r.note, r.status,
                       r.requested_by as requestedBy, r.requested_at as requestedAt, r.answer
                  from supply_requests r left join inventory_items i on i.id = r.item_id where r.id = ?""", id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("La solicitud"));
    }

    /** Recepción entrega (descuenta stock) o rechaza. Bloquea la solicitud para que no se entregue dos veces. */
    @Transactional
    public Map<String, Object> answer(Long id, Answer a) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from supply_requests where id = ? for update", id);
        if (rows.isEmpty()) throw ApiException.notFound("La solicitud");
        Map<String, Object> r = rows.get(0);
        if (!"PENDIENTE".equals(r.get("status"))) throw ApiException.conflict("YA_ATENDIDA", "Esta solicitud ya fue atendida.");
        String action = a.action() == null ? "" : a.action().trim().toUpperCase();
        String text = a.answer() == null || a.answer().isBlank() ? null : a.answer().trim();
        if ("ENTREGAR".equals(action)) {
            if (r.get("item_id") == null) throw ApiException.badRequest("SIN_MATERIAL", "Es un material fuera del catálogo: regístralo primero en inventario o recházalo.");
            BigDecimal q = a.quantity() == null ? (BigDecimal) r.get(QUANTITY) : a.quantity();
            if (q.signum() <= 0) throw ApiException.field(QUANTITY, "Cantidad mayor a cero.");
            InventoryItem i = items.lockById(((Number) r.get("item_id")).longValue()).orElseThrow(() -> ApiException.notFound("El material"));
            inventory.deliver(i, q.setScale(2, RoundingMode.HALF_UP), id, "Entregado a " + r.get("requested_by") + " (solicitud #" + id + ")");
            jdbc.update("update supply_requests set status = 'ENTREGADA', answered_by = ?, answered_at = ?, answer = ?, quantity = ? where id = ?",
                    CurrentUser.usernameOrSystem(), Dates.now(), text, q, id);
        } else if ("RECHAZAR".equals(action)) {
            if (text == null) throw ApiException.field("answer", "Indica el motivo del rechazo.");
            jdbc.update("update supply_requests set status = 'RECHAZADA', answered_by = ?, answered_at = ?, answer = ? where id = ?",
                    CurrentUser.usernameOrSystem(), Dates.now(), text, id);
        } else {
            throw ApiException.field("action", "Elige entregar o rechazar.");
        }
        audit.register("SOLICITUD_INSUMO", id, action, text == null ? "" : text);
        return get(id);
    }

    /** El que pidió puede anular mientras no se atienda. */
    @Transactional
    public Map<String, Object> cancel(Long id) {
        int n = jdbc.update("update supply_requests set status = 'ANULADA', answered_at = ? where id = ? and status = 'PENDIENTE' and lower(requested_by) = lower(?)",
                Dates.now(), id, CurrentUser.usernameOrSystem());
        if (n == 0 && !PermissionService.has("INVENTARIO")) throw ApiException.conflict("NO_ANULABLE", "Solo puedes anular tus solicitudes pendientes.");
        return get(id);
    }
}
