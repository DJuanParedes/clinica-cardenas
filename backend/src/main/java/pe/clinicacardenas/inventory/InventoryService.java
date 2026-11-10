package pe.clinicacardenas.inventory;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.clinical.TreatmentItemDone;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Inventario de materiales: stock, entradas, salidas, consumo automático por procedimiento y alertas de stock bajo. */
@Service
public class InventoryService {

    public static final List<String> MOVE_KINDS = List.of("ENTRADA", "SALIDA", "AJUSTE");

    private final InventoryItemRepository items;
    private final InventoryMoveRepository moves;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public InventoryService(InventoryItemRepository items, InventoryMoveRepository moves, JdbcTemplate jdbc, AuditService audit) {
        this.items = items;
        this.moves = moves;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record ItemRequest(String name, String unit, BigDecimal minStock, BigDecimal initialStock, Boolean active) {}

    public record MoveRequest(String kind, BigDecimal quantity, String reason) {}

    public record SupplyRequest(Long itemId, BigDecimal quantity) {}

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<InventoryItem> all = items.findAllByOrderByActiveDescNameAsc();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", all);
        m.put("lowCount", all.stream().filter(InventoryItem::isLow).count());
        m.put("recent", recentMoves());
        return m;
    }

    private List<Map<String, Object>> recentMoves() {
        return jdbc.queryForList("""
                select m.id, m.kind, m.quantity, m.stock_after as stockAfter, m.reason, m.created_at as createdAt,
                       m.created_by as createdBy, i.name as item, i.unit
                  from inventory_moves m join inventory_items i on i.id = m.item_id
                 order by m.id desc limit 40
                """);
    }

    public long lowCount() {
        return items.countLow();
    }

    @Transactional
    public InventoryItem create(ItemRequest r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) throw ApiException.field("name", "Escribe el nombre del material.");
        if (items.existsByNameIgnoreCase(name)) throw ApiException.field("name", "Ya existe un material con ese nombre.");
        InventoryItem i = new InventoryItem();
        i.name = name;
        i.unit = r.unit() == null || r.unit().isBlank() ? "unidad" : r.unit().trim();
        i.minStock = nonNegative(r.minStock(), "minStock");
        i.stock = BigDecimal.ZERO;
        i.createdAt = Dates.now();
        items.save(i);
        BigDecimal initial = nonNegative(r.initialStock(), "initialStock");
        if (initial.signum() > 0) move(i, "ENTRADA", initial, "Stock inicial", null);
        audit.record("INVENTARIO", i.id, "CREAR", name);
        return i;
    }

    @Transactional
    public InventoryItem update(Long id, ItemRequest r) {
        InventoryItem i = items.findById(id).orElseThrow(() -> ApiException.notFound("El material"));
        if (r.name() != null && !r.name().isBlank() && !r.name().trim().equalsIgnoreCase(i.name)) {
            if (items.existsByNameIgnoreCase(r.name().trim())) throw ApiException.field("name", "Ya existe un material con ese nombre.");
            i.name = r.name().trim();
        }
        if (r.unit() != null && !r.unit().isBlank()) i.unit = r.unit().trim();
        if (r.minStock() != null) i.minStock = nonNegative(r.minStock(), "minStock");
        if (r.active() != null) i.active = r.active();
        i.updatedAt = Dates.now();
        items.save(i);
        return i;
    }

    @Transactional
    public Map<String, Object> registerMove(Long id, MoveRequest r) {
        InventoryItem i = items.lockById(id).orElseThrow(() -> ApiException.notFound("El material"));
        String kind = r.kind() == null ? "" : r.kind().trim().toUpperCase();
        if (!MOVE_KINDS.contains(kind)) throw ApiException.field("kind", "Elige entrada, salida o ajuste.");
        if (r.quantity() == null || r.quantity().signum() == 0) throw ApiException.field("quantity", "Ingresa la cantidad.");
        BigDecimal q = r.quantity().setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = switch (kind) {
            case "ENTRADA" -> q.abs();
            case "SALIDA" -> q.abs().negate();
            default -> q.subtract(i.stock);   // AJUSTE: la cantidad es el stock contado
        };
        if (!"AJUSTE".equals(kind) && q.signum() < 0) throw ApiException.field("quantity", "La cantidad debe ser positiva.");
        if ("AJUSTE".equals(kind) && q.signum() < 0) throw ApiException.field("quantity", "El stock contado no puede ser negativo.");
        if ("SALIDA".equals(kind) && i.stock.add(delta).signum() < 0) {
            throw ApiException.field("quantity", "Solo hay " + i.stock.stripTrailingZeros().toPlainString() + " " + i.unit + " en stock.");
        }
        String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().trim();
        move(i, kind, delta, reason, null);
        return Map.of("item", i, "message", "Movimiento registrado.");
    }

    private void move(InventoryItem i, String kind, BigDecimal delta, String reason, Long treatmentItemId) {
        i.stock = i.stock.add(delta).setScale(2, RoundingMode.HALF_UP);
        i.updatedAt = Dates.now();
        items.save(i);
        InventoryMove m = new InventoryMove();
        m.itemId = i.id;
        m.kind = kind;
        m.quantity = delta;
        m.stockAfter = i.stock;
        m.reason = reason;
        m.treatmentItemId = treatmentItemId;
        m.createdAt = Dates.now();
        m.createdBy = CurrentUser.usernameOrSystem();
        moves.save(m);
    }

    @Transactional(readOnly = true)
    public List<InventoryMove> moves(Long id) {
        return moves.findTop50ByItemIdOrderByIdDesc(id);
    }

    // ------------------------------------------------------------------ consumo por procedimiento

    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplies() {
        return jdbc.queryForList("""
                select p.id as procedureId, p.code, p.name as procedureName, i.id as itemId, i.name as itemName, i.unit,
                       s.quantity
                  from procedure_supplies s join procedures p on p.id = s.procedure_id
                  join inventory_items i on i.id = s.item_id
                 order by p.code, i.name
                """);
    }

    @Transactional
    public List<Map<String, Object>> saveSupplies(Long procedureId, List<SupplyRequest> list) {
        Integer exists = jdbc.queryForObject("select count(*) from procedures where id = ?", Integer.class, procedureId);
        if (exists == null || exists == 0) throw ApiException.notFound("El procedimiento");
        jdbc.update("delete from procedure_supplies where procedure_id = ?", procedureId);
        if (list != null) {
            for (SupplyRequest s : list) {
                if (s.itemId() == null || s.quantity() == null || s.quantity().signum() <= 0) continue;
                items.findById(s.itemId()).orElseThrow(() -> ApiException.notFound("El material"));
                jdbc.update("insert into procedure_supplies (procedure_id, item_id, quantity) values (?, ?, ?)",
                        procedureId, s.itemId(), s.quantity());
            }
        }
        return supplies();
    }

    /** Al marcar un procedimiento como realizado se descuentan sus materiales (una sola vez). */
    @EventListener
    public void onTreatmentDone(TreatmentItemDone e) {
        if (moves.existsByTreatmentItemIdAndKind(e.treatmentItemId(), "CONSUMO")) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select item_id, quantity from procedure_supplies where procedure_id = ?", e.procedureId());
        for (Map<String, Object> r : rows) {
            Long itemId = ((Number) r.get("item_id")).longValue();
            BigDecimal q = new BigDecimal(String.valueOf(r.get("quantity")));
            items.lockById(itemId).ifPresent(i -> move(i, "CONSUMO", q.negate(), e.description(), e.treatmentItemId()));
        }
    }

    private static BigDecimal nonNegative(BigDecimal v, String field) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw ApiException.field(field, "No puede ser negativo.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
