package pe.clinicacardenas.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.cash.Expense;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * Compras de insumos a proveedores. Una compra registra, en una sola transacción:
 * los lotes que entran (con su vencimiento y costo), el movimiento de stock y, si se pagó al contado,
 * el egreso en caja (categoría INSUMOS) con el número de factura o boleta del proveedor.
 * Si es al crédito queda "por pagar" con fecha de vencimiento y el egreso se genera cuando se paga.
 */
@Service
public class InventoryPurchaseService {

    public static final List<String> DOC_TYPES = List.of("FACTURA", "BOLETA", "GUIA", "SIN_COMPROBANTE");

    private final InventoryItemRepository items;
    private final InventoryService inventory;
    private final CashService cash;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public InventoryPurchaseService(InventoryItemRepository items, InventoryService inventory, CashService cash, JdbcTemplate jdbc, AuditService audit) {
        this.items = items;
        this.inventory = inventory;
        this.cash = cash;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Line(Long itemId, BigDecimal quantity, BigDecimal unitCost, String lotCode, LocalDate expiresOn) {}

    public record PurchaseRequest(LocalDate purchaseDate, String supplier, String docType, String docNumber,
                                  Boolean paid, String method, LocalDate dueDate, String notes, List<Line> lines) {}

    @Transactional
    public Map<String, Object> register(PurchaseRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate date = r.purchaseDate() == null ? Dates.today() : r.purchaseDate();
        if (date.isAfter(Dates.today())) e.put("purchaseDate", "La fecha no puede ser futura.");
        String supplier = r.supplier() == null ? "" : r.supplier().trim();
        if (supplier.length() < 3) e.put("supplier", "Indica el proveedor o laboratorio.");
        String docType = r.docType() == null ? "" : r.docType().trim().toUpperCase();
        if (!DOC_TYPES.contains(docType)) e.put("docType", "Selecciona el comprobante del proveedor.");
        String docNumber = r.docNumber() == null ? null : r.docNumber().trim().toUpperCase();
        if (("FACTURA".equals(docType) || "BOLETA".equals(docType)) && (docNumber == null || !docNumber.matches("[A-Z0-9]{1,4}-\\d{1,8}"))) {
            e.put("docNumber", "Escribe la serie y número, por ejemplo F001-00012345.");
        }
        boolean paid = r.paid() == null || r.paid();
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (paid && (method == null || !CashService.METHODS.contains(method))) e.put("method", "Selecciona cómo se pagó.");
        if (!paid && (r.dueDate() == null || r.dueDate().isBefore(date))) e.put("dueDate", "Indica hasta cuándo hay que pagarle al proveedor.");
        if (r.lines() == null || r.lines().isEmpty()) e.put("lines", "Agrega al menos un producto.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos de la compra.", e);

        BigDecimal total = BigDecimal.ZERO;
        for (int k = 0; k < r.lines().size(); k++) {
            Line l = r.lines().get(k);
            if (l.itemId() == null) throw ApiException.field("lines", "Fila " + (k + 1) + ": elige el producto.");
            if (l.quantity() == null || l.quantity().signum() <= 0) throw ApiException.field("lines", "Fila " + (k + 1) + ": cantidad mayor a cero.");
            if (l.unitCost() == null || l.unitCost().signum() < 0) throw ApiException.field("lines", "Fila " + (k + 1) + ": ingresa el costo unitario.");
            if (l.expiresOn() != null && !l.expiresOn().isAfter(date)) throw ApiException.field("lines", "Fila " + (k + 1) + ": el lote ya está vencido; no lo recibas.");
            total = total.add(l.quantity().multiply(l.unitCost()));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        if (docNumber != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*) > 0 from inventory_purchases where supplier = ? and doc_number = ? and status <> 'ANULADA'", Boolean.class, supplier, docNumber))) {
            throw ApiException.field("docNumber", "Esa factura/boleta de ese proveedor ya fue registrada.");
        }

        final BigDecimal fTotal = total;
        final String fDoc = docNumber;
        final String fMethod = paid ? method : null;
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into inventory_purchases (purchase_date, supplier, doc_type, doc_number, total, method, notes, status, due_date, created_at, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, date);
            ps.setString(2, supplier);
            ps.setString(3, docType);
            ps.setString(4, fDoc);
            ps.setBigDecimal(5, fTotal);
            ps.setString(6, fMethod);
            ps.setString(7, r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
            ps.setString(8, paid ? "PAGADA" : "POR_PAGAR");
            ps.setObject(9, paid ? null : r.dueDate());
            ps.setObject(10, Dates.now());
            ps.setString(11, CurrentUser.usernameOrSystem());
            return ps;
        }, kh);
        long id = java.util.Objects.requireNonNull(kh.getKey()).longValue();

        for (Line l : r.lines()) {
            InventoryItem i = items.lockById(l.itemId()).orElseThrow(() -> ApiException.notFound("El material"));
            inventory.receive(i, id, l.quantity().setScale(2, RoundingMode.HALF_UP), l.unitCost(), l.lotCode(), l.expiresOn(),
                    "Compra " + docType.toLowerCase() + (docNumber == null ? "" : " " + docNumber) + " · " + supplier);
            i.unitCost = l.unitCost().setScale(2, RoundingMode.HALF_UP);
            if (i.supplier == null) i.supplier = supplier;
            items.save(i);
        }
        if (paid) createExpense(id, date, supplier, docType, docNumber, total, method);
        audit.record("COMPRA", id, "REGISTRAR", supplier + " · S/ " + total.toPlainString() + (paid ? "" : " · al crédito"));
        return get(id);
    }

    private void createExpense(long purchaseId, LocalDate date, String supplier, String docType, String docNumber, BigDecimal total, String method) {
        LocalDate d = date.isBefore(Dates.today().minusDays(60)) ? Dates.today() : date;
        String concept = "Compra de insumos · " + supplier;
        Expense x = cash.registerExpense(new CashService.ExpenseRequest(d, "INSUMOS",
                concept.length() > 200 ? concept.substring(0, 200) : concept, total, method,
                docNumber == null ? docType : docType + " " + docNumber, supplier));
        jdbc.update("update inventory_purchases set expense_id = ?, method = ?, status = 'PAGADA' where id = ?", x.id, method, purchaseId);
    }

    public record PayRequest(String method, LocalDate paidOn) {}

    /** Paga una compra al crédito: recién ahí sale el dinero de caja. */
    @Transactional
    public Map<String, Object> pay(Long id, PayRequest r) {
        Map<String, Object> p = get(id);
        if (!"POR_PAGAR".equals(p.get("status"))) throw ApiException.conflict("COMPRA_NO_PENDIENTE", "Esta compra no está pendiente de pago.");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !CashService.METHODS.contains(method)) throw ApiException.field("method", "Selecciona cómo se pagó.");
        createExpense(id, r.paidOn() == null ? Dates.today() : r.paidOn(), (String) p.get("supplier"), (String) p.get("docType"),
                (String) p.get("docNumber"), (BigDecimal) p.get("total"), method);
        audit.record("COMPRA", id, "PAGAR", "S/ " + p.get("total"));
        return get(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, purchase_date as purchaseDate, supplier, doc_type as docType, doc_number as docNumber, total, method,
                       expense_id as expenseId, notes, status, due_date as dueDate, created_at as createdAt, created_by as createdBy
                  from inventory_purchases where id = ?""", id);
        if (rows.isEmpty()) throw ApiException.notFound("La compra");
        Map<String, Object> m = rows.get(0);
        m.put("lines", jdbc.queryForList("""
                select l.id, i.name as item, i.unit, l.lot_code as lotCode, l.expires_on as expiresOn, l.quantity, l.remaining, l.unit_cost as unitCost
                  from inventory_lots l join inventory_items i on i.id = l.item_id where l.purchase_id = ? order by l.id""", id));
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? Dates.today() : to;
        LocalDate f = from == null ? t.minusMonths(3) : from;
        return jdbc.queryForList("""
                select p.id, p.purchase_date as purchaseDate, p.supplier, p.doc_type as docType, p.doc_number as docNumber, p.total,
                       p.method, p.status, p.due_date as dueDate, p.expense_id as expenseId,
                       (select count(*) from inventory_lots l where l.purchase_id = p.id) as lineCount
                  from inventory_purchases p where p.purchase_date between ? and ? order by p.purchase_date desc, p.id desc""", f, t);
    }
}
