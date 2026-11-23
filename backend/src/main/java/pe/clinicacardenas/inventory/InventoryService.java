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

/**
 * Inventario de materiales: stock por lotes con fecha de vencimiento (las salidas consumen primero el lote que vence
 * antes, FEFO), compras a proveedores que generan el egreso en caja, consumo automático por procedimiento, alertas
 * de stock bajo y de vencimiento. Todo cambio de stock bloquea la fila del material (SELECT … FOR UPDATE), así dos
 * personas descontando a la vez no pueden dejar el stock negativo ni perder un movimiento.
 */
@Service
public class InventoryService {
    private static final String AJUSTE_2 = "AJUSTE";
    private static final String ENTRADA = "ENTRADA";
    private static final String EL_MATERIAL = "El material";
    private static final String MEDICAMENTO = "MEDICAMENTO";
    private static final String SALIDA = "SALIDA";
    private static final String EXPIRESON = "expiresOn";
    private static final String QUANTITY = "quantity";


    public static final List<String> MOVE_KINDS = List.of(ENTRADA, SALIDA, AJUSTE_2);
    public static final List<String> CATEGORIES = List.of("INSUMO", "INSTRUMENTAL", MEDICAMENTO);
    /** Días de anticipación para avisar que un lote está por vencer. */
    public static final int EXPIRY_WARNING_DAYS = 60;

    private final InventoryItemRepository items;
    private final InventoryMoveRepository moves;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    private pe.clinicacardenas.files.ImageService images;

    @org.springframework.beans.factory.annotation.Autowired
    void setImages(pe.clinicacardenas.files.ImageService images) {
        this.images = images;
    }

    public InventoryService(InventoryItemRepository items, InventoryMoveRepository moves, JdbcTemplate jdbc, AuditService audit) {
        this.items = items;
        this.moves = moves;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record ItemRequest(String name, String unit, BigDecimal minStock, BigDecimal initialStock, Boolean active,
                              String category, String brand, String supplier, BigDecimal unitCost, Boolean tracksExpiry,
                              String lotCode, java.time.LocalDate expiresOn) {
        public ItemRequest(String name, String unit, BigDecimal minStock, BigDecimal initialStock, Boolean active) {
            this(name, unit, minStock, initialStock, active, null, null, null, null, null, null, null);
        }
    }

    public record MoveRequest(String kind, BigDecimal quantity, String reason, String lotCode, java.time.LocalDate expiresOn,
                              BigDecimal unitCost, Long patientId) {
        public MoveRequest(String kind, BigDecimal quantity, String reason) {
            this(kind, quantity, reason, null, null, null, null);
        }
    }

    /** v2.8: costo de lo que sale de los lotes (FEFO) y el primer lote tocado. */
    private record Take(Long lot, BigDecimal cost) {}

    public record SupplyRequest(Long itemId, BigDecimal quantity) {}

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<InventoryItem> all = items.findAllByOrderByActiveDescNameAsc();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", all);
        m.put("lowCount", all.stream().filter(InventoryItem::isLow).count());
        m.put("recent", recentMoves());
        List<Map<String, Object>> exp = expiring();
        m.put("expiring", exp);
        m.put("expiredCount", exp.stream().filter(x -> Boolean.TRUE.equals(x.get("expired"))).count());
        m.put("nextExpiry", nextExpiryByItem());
        m.put("value", jdbc.queryForObject("select coalesce(sum(l.remaining * coalesce(l.unit_cost, i.unit_cost, 0)), 0) from inventory_lots l join inventory_items i on i.id = l.item_id where l.remaining > 0", BigDecimal.class));
        m.put("pendingRequests", jdbc.queryForObject("select count(*) from supply_requests where status = 'PENDIENTE'", Long.class));
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

    /** Lotes con saldo que ya vencieron o vencen en los próximos 60 días. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiring() {
        java.time.LocalDate today = Dates.today();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select l.id, l.item_id as itemId, i.name as item, i.unit, i.brand, l.lot_code as lotCode, l.expires_on as expiresOn,
                       l.remaining
                  from inventory_lots l join inventory_items i on i.id = l.item_id
                 where l.remaining > 0 and l.expires_on is not null and l.expires_on <= ? and i.active = true
                 order by l.expires_on
                """, today.plusDays(EXPIRY_WARNING_DAYS));
        for (Map<String, Object> r : rows) {
            java.time.LocalDate d = ((java.sql.Date) r.get(EXPIRESON)).toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, d);
            r.put("days", days);
            r.put("expired", days < 0);
        }
        return rows;
    }

    private Map<Long, Object> nextExpiryByItem() {
        Map<Long, Object> m = new HashMap<>();
        jdbc.query("select item_id, min(expires_on) from inventory_lots where remaining > 0 and expires_on is not null group by item_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> m.put(rs.getLong(1), rs.getDate(2).toLocalDate()));
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lots(Long itemId) {
        return jdbc.queryForList("""
                select l.id, l.lot_code as lotCode, l.expires_on as expiresOn, l.quantity, l.remaining, l.unit_cost as unitCost,
                       l.created_at as createdAt, p.supplier, p.doc_type as docType, p.doc_number as docNumber
                  from inventory_lots l left join inventory_purchases p on p.id = l.purchase_id
                 where l.item_id = ? order by (l.remaining > 0) desc, l.expires_on is null, l.expires_on, l.id
                """, itemId);
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
        applyDetails(i, r);
        i.stock = BigDecimal.ZERO;
        i.createdAt = Dates.now();
        items.save(i);
        BigDecimal initial = nonNegative(r.initialStock(), "initialStock");
        if (initial.signum() > 0) {
            checkExpiry(i, r.expiresOn());
            Long lot = newLot(i, null, r.lotCode(), r.expiresOn(), initial, i.unitCost);
            move(i, ENTRADA, initial, "Stock inicial", null, lot, null, null);
        }
        audit.register("INVENTARIO", i.id, "CREAR", name);
        return i;
    }

    @Transactional
    public InventoryItem update(Long id, ItemRequest r) {
        InventoryItem i = items.findById(id).orElseThrow(() -> ApiException.notFound(EL_MATERIAL));
        if (r.name() != null && !r.name().isBlank() && !r.name().trim().equalsIgnoreCase(i.name)) {
            if (items.existsByNameIgnoreCase(r.name().trim())) throw ApiException.field("name", "Ya existe un material con ese nombre.");
            i.name = r.name().trim();
        }
        if (r.unit() != null && !r.unit().isBlank()) i.unit = r.unit().trim();
        if (r.minStock() != null) i.minStock = nonNegative(r.minStock(), "minStock");
        if (r.active() != null) i.active = r.active();
        applyDetails(i, r);
        i.updatedAt = Dates.now();
        items.save(i);
        return i;
    }

    private void applyDetails(InventoryItem i, ItemRequest r) {
        if (r.category() != null && !r.category().isBlank()) {
            String c = r.category().trim().toUpperCase();
            if (!CATEGORIES.contains(c)) throw ApiException.field("category", "Elige insumo, instrumental o medicamento.");
            i.category = c;
        }
        if (r.brand() != null) i.brand = blank(r.brand(), 80);
        if (r.supplier() != null) i.supplier = blank(r.supplier(), 120);
        if (r.unitCost() != null) i.unitCost = nonNegative(r.unitCost(), "unitCost");
        if (r.tracksExpiry() != null) i.tracksExpiry = r.tracksExpiry();
        else if (i.id == null) i.tracksExpiry = !"INSTRUMENTAL".equals(i.category);
    }

    private static String blank(String v, int max) {
        String t = v == null ? null : v.trim();
        if (t == null || t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private void checkExpiry(InventoryItem i, java.time.LocalDate expiresOn) {
        if (expiresOn != null && expiresOn.isBefore(Dates.today())) {
            throw ApiException.field(EXPIRESON, "Ese lote ya está vencido: no debe ingresar al inventario (DIGEMID).");
        }
        if (i.tracksExpiry && MEDICAMENTO.equals(i.category) && expiresOn == null) {
            throw ApiException.field(EXPIRESON, "Los medicamentos deben registrarse con su fecha de vencimiento.");
        }
    }

    private Long newLot(InventoryItem i, Long purchaseId, String lotCode, java.time.LocalDate expiresOn, BigDecimal q, BigDecimal unitCost) {
        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c -> {
            java.sql.PreparedStatement ps = c.prepareStatement(
                    "insert into inventory_lots (item_id, purchase_id, lot_code, expires_on, quantity, remaining, unit_cost, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, i.id);
            ps.setObject(2, purchaseId);
            ps.setString(3, blank(lotCode, 40));
            ps.setObject(4, expiresOn);
            ps.setBigDecimal(5, q);
            ps.setBigDecimal(6, q);
            ps.setBigDecimal(7, unitCost);
            ps.setObject(8, Dates.now());
            return ps;
        }, kh);
        return java.util.Objects.requireNonNull(kh.getKey()).longValue();
    }

    /** Descuenta de los lotes: primero el que vence antes (FEFO); los vencidos no se usan en pacientes. Devuelve el primer lote tocado. */
    private Take consumeLots(InventoryItem i, BigDecimal qty, boolean allowExpired) {
        BigDecimal left = qty;
        BigDecimal cost = BigDecimal.ZERO;
        Long first = null;
        List<Map<String, Object>> lots = jdbc.queryForList(
                "select id, remaining, expires_on, unit_cost from inventory_lots where item_id = ? and remaining > 0 order by expires_on is null, expires_on, id for update", i.id);
        for (Map<String, Object> l : lots) {
            if (left.signum() <= 0) break;
            Object exp = l.get("expires_on");
            if (!allowExpired && exp != null && ((java.sql.Date) exp).toLocalDate().isBefore(Dates.today())) continue;
            BigDecimal rem = (BigDecimal) l.get("remaining");
            BigDecimal take = rem.min(left);
            jdbc.update("update inventory_lots set remaining = remaining - ? where id = ?", take, l.get("id"));
            if (first == null) first = ((Number) l.get("id")).longValue();
            BigDecimal uc = l.get("unit_cost") == null ? averageCost(i) : (BigDecimal) l.get("unit_cost");
            cost = cost.add(take.multiply(uc));
            left = left.subtract(take);
        }
        // Stock sin lote (registros antiguos): se descuenta igual del total, al costo promedio
        if (left.signum() > 0) cost = cost.add(left.multiply(averageCost(i)));
        return new Take(first, cost.setScale(2, RoundingMode.HALF_UP));
    }

    /** Costo promedio actual: valor del stock / cantidad; si no hay, el último costo registrado. */
    BigDecimal averageCost(InventoryItem i) {
        List<BigDecimal> v = jdbc.queryForList("select value_after from inventory_moves where item_id = ? order by id desc limit 1", BigDecimal.class, i.id);
        if (!v.isEmpty() && v.get(0) != null && i.stock.signum() > 0 && v.get(0).signum() > 0)
            return v.get(0).divide(i.stock, 4, RoundingMode.HALF_UP);
        return i.unitCost == null ? BigDecimal.ZERO : i.unitCost;
    }

    /** Valor del stock antes del movimiento; sin historial, el stock actual al último costo. */
    private BigDecimal lastValue(InventoryItem i) {
        List<BigDecimal> v = jdbc.queryForList("select value_after from inventory_moves where item_id = ? order by id desc limit 1", BigDecimal.class, i.id);
        if (v.isEmpty() || v.get(0) == null)
            return i.stock.max(BigDecimal.ZERO).multiply(i.unitCost == null ? BigDecimal.ZERO : i.unitCost).setScale(2, RoundingMode.HALF_UP);
        return v.get(0);
    }

    /** Ingreso de un lote comprado (lo usa la compra a proveedor). El material ya viene bloqueado. */
    void receive(InventoryItem i, Long purchaseId, BigDecimal q, BigDecimal unitCost, String lotCode, java.time.LocalDate expiresOn, String reason) {
        if (i.tracksExpiry && MEDICAMENTO.equals(i.category) && expiresOn == null) {
            throw ApiException.field("lines", i.name + ": los medicamentos se registran con su fecha de vencimiento.");
        }
        Long lot = newLot(i, purchaseId, lotCode, expiresOn, q, unitCost);
        move(i, ENTRADA, q, reason, null, lot, purchaseId, null, q.multiply(unitCost == null ? BigDecimal.ZERO : unitCost).setScale(2, RoundingMode.HALF_UP), null, null);
    }

    /** Entrega de una solicitud del odontólogo: salida FEFO sin usar lotes vencidos. */
    void deliver(InventoryItem i, BigDecimal q, Long requestId, String reason) {
        if (i.stock.compareTo(q) < 0) {
            throw ApiException.field(QUANTITY, "Solo hay " + i.stock.stripTrailingZeros().toPlainString() + " " + i.unit + " en stock.");
        }
        Take t = consumeLots(i, q, false);
        move(i, SALIDA, q.negate(), reason, null, t.lot(), null, requestId, t.cost().negate(), null, null);
    }

    @Transactional
    public InventoryItem setImage(Long id, org.springframework.web.multipart.MultipartFile file) {
        InventoryItem i = items.findById(id).orElseThrow(() -> ApiException.notFound(EL_MATERIAL));
        String old = i.imagePath;
        i.imagePath = images.save("productos", file);
        i.updatedAt = Dates.now();
        items.save(i);
        images.delete(old);
        audit.register("INVENTARIO", i.id, "FOTO", i.name);
        return i;
    }

    @Transactional
    public InventoryItem removeImage(Long id) {
        InventoryItem i = items.findById(id).orElseThrow(() -> ApiException.notFound(EL_MATERIAL));
        images.delete(i.imagePath);
        i.imagePath = null;
        i.updatedAt = Dates.now();
        items.save(i);
        return i;
    }

    @Transactional
    public Map<String, Object> registerMove(Long id, MoveRequest r) {
        InventoryItem i = items.lockById(id).orElseThrow(() -> ApiException.notFound(EL_MATERIAL));
        String kind = r.kind() == null ? "" : r.kind().trim().toUpperCase();
        if (!MOVE_KINDS.contains(kind)) throw ApiException.field("kind", "Elige entrada, salida o ajuste.");
        if (r.quantity() == null || r.quantity().signum() == 0) throw ApiException.field(QUANTITY, "Ingresa la cantidad.");
        BigDecimal q = r.quantity().setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = switch (kind) {
            case ENTRADA -> q.abs();
            case SALIDA -> q.abs().negate();
            default -> q.subtract(i.stock);   // AJUSTE: la cantidad es el stock contado
        };
        if (!AJUSTE_2.equals(kind) && q.signum() < 0) throw ApiException.field(QUANTITY, "La cantidad debe ser positiva.");
        if (AJUSTE_2.equals(kind) && q.signum() < 0) throw ApiException.field(QUANTITY, "El stock contado no puede ser negativo.");
        if (SALIDA.equals(kind) && i.stock.add(delta).signum() < 0) {
            throw ApiException.field(QUANTITY, "Solo hay " + i.stock.stripTrailingZeros().toPlainString() + " " + i.unit + " en stock.");
        }
        String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().trim();
        if (SALIDA.equals(kind) && reason == null) throw ApiException.field("reason", "Indica el motivo de la salida (merma, vencido, préstamo…).");
        Long lot = null;
        BigDecimal cost = BigDecimal.ZERO;
        if (r.unitCost() != null && r.unitCost().signum() < 0) throw ApiException.field("unitCost", "El costo no puede ser negativo.");
        if (r.patientId() != null && (!SALIDA.equals(kind)
                || jdbc.queryForObject("select count(*) from patients where id = ?", Integer.class, r.patientId()) == 0))
            throw ApiException.field("patientId", "Solo una salida puede atribuirse a un paciente existente.");
        if (delta.signum() > 0) {
            checkExpiry(i, r.expiresOn());
            BigDecimal uc = r.unitCost() != null ? r.unitCost() : averageCost(i);
            lot = newLot(i, null, r.lotCode(), r.expiresOn(), delta, uc);
            cost = delta.multiply(uc).setScale(2, RoundingMode.HALF_UP);
        } else if (delta.signum() < 0) {
            // Una salida manual puede retirar lotes vencidos (para darlos de baja); el consumo clínico no.
            Take t = consumeLots(i, delta.abs(), true);
            lot = t.lot();
            cost = t.cost().negate();
        }
        move(i, kind, delta, reason, null, lot, null, null, cost, r.patientId(), null);
        return Map.of("item", i, "message", "Movimiento registrado.");
    }

    private void move(InventoryItem i, String kind, BigDecimal delta, String reason, Long treatmentItemId,
                      Long lotId, Long purchaseId, Long requestId) {
        move(i, kind, delta, reason, treatmentItemId, lotId, purchaseId, requestId, delta.multiply(averageCost(i)).setScale(2, RoundingMode.HALF_UP), null, null);
    }

    /** {@code cost}: valor que entra (+) o sale (-) del inventario con este movimiento. */
    private void move(InventoryItem i, String kind, BigDecimal delta, String reason, Long treatmentItemId,
                      Long lotId, Long purchaseId, Long requestId, BigDecimal cost, Long patientId, Long procedureId) {
        BigDecimal before = lastValue(i);
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
        m.lotId = lotId;
        m.purchaseId = purchaseId;
        m.requestId = requestId;
        m.totalCost = cost == null ? BigDecimal.ZERO : cost.setScale(2, RoundingMode.HALF_UP);
        m.unitCost = delta.signum() == 0 ? null : m.totalCost.abs().divide(delta.abs(), 4, RoundingMode.HALF_UP);
        // sin stock no queda valor (evita restos por redondeo)
        m.valueAfter = i.stock.signum() <= 0 ? BigDecimal.ZERO : before.add(m.totalCost).max(BigDecimal.ZERO);
        m.patientId = patientId;
        m.procedureId = procedureId;
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

    /** v2.10: kit del procedimiento de un ítem del plan y catálogo de materiales, para marcar lo usado en la atención. */
    @Transactional(readOnly = true)
    public Map<String, Object> usageForm(Long treatmentItemId) {
        List<Long> proc = jdbc.queryForList("select procedure_id from treatment_items where id = ?", Long.class, treatmentItemId);
        if (proc.isEmpty()) throw ApiException.notFound("El procedimiento del plan");
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("kit", jdbc.queryForList("""
                select i.id as itemId, i.name, i.unit, i.stock, s.quantity
                  from procedure_supplies s join inventory_items i on i.id = s.item_id
                 where s.procedure_id = ? order by i.name
                """, proc.get(0)));
        m.put("catalog", jdbc.queryForList("select id as itemId, name, unit, stock from inventory_items where active = true order by name"));
        return m;
    }

    @Transactional
    public List<Map<String, Object>> saveSupplies(Long procedureId, List<SupplyRequest> list) {
        Integer exists = jdbc.queryForObject("select count(*) from procedures where id = ?", Integer.class, procedureId);
        if (exists == null || exists == 0) throw ApiException.notFound("El procedimiento");
        jdbc.update("delete from procedure_supplies where procedure_id = ?", procedureId);
        if (list != null) {
            for (SupplyRequest s : list) {
                if (s.itemId() == null || s.quantity() == null || s.quantity().signum() <= 0) continue;
                if (!items.existsById(s.itemId())) throw ApiException.notFound(EL_MATERIAL);
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
        // v2.10: lo que el odontólogo marcó como usado en esta atención; si no indicó nada, el kit estándar.
        List<Map<String, Object>> rows = e.used() != null
                ? e.used().stream().filter(u -> u.itemId() != null && u.quantity() != null && u.quantity().signum() > 0)
                    .map(u -> Map.<String, Object>of("item_id", u.itemId(), QUANTITY, u.quantity())).toList()
                : jdbc.queryForList("select item_id, quantity from procedure_supplies where procedure_id = ?", e.procedureId());
        List<Long> pat = jdbc.queryForList("select tp.patient_id from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id where ti.id = ?",
                Long.class, e.treatmentItemId());
        Long patientId = pat.isEmpty() ? null : pat.get(0);
        for (Map<String, Object> r : rows) {
            Long itemId = ((Number) r.get("item_id")).longValue();
            BigDecimal q = new BigDecimal(String.valueOf(r.get(QUANTITY)));
            // El stock nunca queda negativo: se descuenta lo que hay y se deja constancia de lo que faltó.
            items.lockById(itemId).ifPresent(i -> {
                BigDecimal used = q.min(i.stock.max(BigDecimal.ZERO));
                String why = e.description();
                if (used.compareTo(q) < 0) {
                    why = (why == null ? "" : why + " · ") + "stock insuficiente: faltaron "
                            + q.subtract(used).stripTrailingZeros().toPlainString() + " " + i.unit;
                }
                Take t = used.signum() > 0 ? consumeLots(i, used, false) : new Take(null, BigDecimal.ZERO);
                move(i, "CONSUMO", used.negate(), why, e.treatmentItemId(), t.lot(), null, null, t.cost().negate(), patientId, e.procedureId());
            });
        }
    }

    private static BigDecimal nonNegative(BigDecimal v, String field) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw ApiException.field(field, "No puede ser negativo.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
