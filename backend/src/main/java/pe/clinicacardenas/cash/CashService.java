package pe.clinicacardenas.cash;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * HU-13 registrar pagos · HU-14 saldo pendiente del paciente · HU-15 ingresos y egresos por período.
 *
 * Cargos: procedimientos del plan de tratamiento marcados como realizados (su precio).
 * Abonos: pagos válidos, ligados a un procedimiento (obligación) o "a cuenta".
 * Saldo = cargos - abonos. Si es negativo, el paciente tiene saldo a favor.
 */
@Service
public class CashService {

    public static final List<String> METHODS = List.of("EFECTIVO", "TARJETA", "YAPE", "PLIN", "TRANSFERENCIA");
    public static final Map<String, String> METHOD_LABEL = Map.of("EFECTIVO", "Efectivo", "TARJETA", "Tarjeta",
            "YAPE", "Yape", "PLIN", "Plin", "TRANSFERENCIA", "Transferencia");
    public static final List<String> EXPENSE_CATEGORIES = List.of("INSUMOS", "LABORATORIO", "ALQUILER", "SERVICIOS",
            "PLANILLA", "MANTENIMIENTO", "MARKETING", "IMPUESTOS", "OTROS");

    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final PatientRepository patients;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CashService(PaymentRepository payments, ExpenseRepository expenses, PatientRepository patients,
                       JdbcTemplate jdbc, AuditService audit) {
        this.payments = payments;
        this.expenses = expenses;
        this.patients = patients;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record PaymentRequest(Long patientId, Long treatmentItemId, BigDecimal amount, String method,
                                 String reference, String notes) {}

    public record ExpenseRequest(LocalDate expenseDate, String category, String concept, BigDecimal amount,
                                 String method, String reference, String supplier) {}

    public record VoidRequest(String reason) {}

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Patient patient(Long id) {
        if (id == null) throw ApiException.field("patientId", "Selecciona al paciente.");
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    // ------------------------------------------------------------------ HU-14 obligaciones y saldo

    /** Procedimientos del paciente con lo cobrado y lo pendiente de cada uno. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obligations(Long patientId) {
        return jdbc.query("""
                select ti.id, ti.status, ti.price, ti.tooth, ti.done_at, pr.name, pr.category, tp.title, tp.id as plan_id,
                       coalesce((select sum(py.amount) from payments py
                                 where py.treatment_item_id = ti.id and py.status = 'VALIDO'), 0) as paid
                  from treatment_items ti
                  join treatment_plans tp on tp.id = ti.plan_id
                  join procedures pr on pr.id = ti.procedure_id
                 where tp.patient_id = ? and (ti.status = 'REALIZADO' or tp.status = 'ACTIVO')
                 order by case when ti.status = 'REALIZADO' then 0 else 1 end, ti.done_at, tp.id, ti.sort_order, ti.id
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            BigDecimal price = money(rs.getBigDecimal("price"));
            BigDecimal paid = money(rs.getBigDecimal("paid"));
            m.put("treatmentItemId", rs.getLong("id"));
            m.put("procedure", rs.getString("name"));
            m.put("category", rs.getString("category"));
            m.put("tooth", rs.getObject("tooth"));
            m.put("planId", rs.getLong("plan_id"));
            m.put("planTitle", rs.getString("title"));
            m.put("status", rs.getString("status"));
            Timestamp done = rs.getTimestamp("done_at");
            m.put("doneAt", done == null ? null : done.toLocalDateTime());
            m.put("price", price);
            m.put("paid", paid);
            m.put("pending", price.subtract(paid).max(BigDecimal.ZERO));
            return m;
        }, patientId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> balance(Long patientId) {
        Patient p = patient(patientId);
        List<Map<String, Object>> obligations = obligations(patientId);
        List<Payment> ps = payments.findByPatientIdOrderByPaidAtDesc(patientId);
        Map<Long, String> itemNames = new HashMap<>();
        for (Map<String, Object> o : obligations) itemNames.put((Long) o.get("treatmentItemId"), (String) o.get("procedure"));

        BigDecimal charges = BigDecimal.ZERO;
        List<Map<String, Object>> moves = new ArrayList<>();
        for (Map<String, Object> o : obligations) {
            if (!"REALIZADO".equals(o.get("status"))) continue;
            charges = charges.add((BigDecimal) o.get("price"));
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("date", o.get("doneAt"));
            mv.put("type", "CARGO");
            mv.put("concept", o.get("procedure") + (o.get("tooth") == null ? "" : " · pieza " + o.get("tooth")));
            mv.put("detail", o.get("planTitle"));
            mv.put("charge", o.get("price"));
            mv.put("credit", BigDecimal.ZERO);
            moves.add(mv);
        }
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal unapplied = BigDecimal.ZERO;
        for (Payment pay : ps) {
            if (!"VALIDO".equals(pay.status)) continue;
            credits = credits.add(pay.amount);
            if (pay.treatmentItemId == null) unapplied = unapplied.add(pay.amount);
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("date", pay.paidAt);
            mv.put("type", "ABONO");
            mv.put("concept", "Pago " + METHOD_LABEL.getOrDefault(pay.method, pay.method).toLowerCase()
                    + (pay.reference == null ? "" : " · op. " + pay.reference));
            mv.put("detail", pay.treatmentItemId == null ? "A cuenta" : itemNames.getOrDefault(pay.treatmentItemId, "Procedimiento"));
            mv.put("charge", BigDecimal.ZERO);
            mv.put("credit", pay.amount);
            mv.put("paymentId", pay.id);
            moves.add(mv);
        }
        moves.sort(Comparator.comparing(m -> (LocalDateTime) m.get("date"), Comparator.nullsLast(Comparator.naturalOrder())));
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> mv : moves) {
            running = running.add((BigDecimal) mv.get("charge")).subtract((BigDecimal) mv.get("credit"));
            mv.put("balance", running);
        }
        Collections.reverse(moves);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient", Map.of("id", p.id, "fullName", p.getFullName(), "docType", p.docType, "docNumber", p.docNumber));
        m.put("charges", money(charges));
        m.put("credits", money(credits));
        BigDecimal balance = money(charges.subtract(credits));
        m.put("balance", balance.max(BigDecimal.ZERO));
        m.put("inFavor", balance.signum() < 0 ? balance.negate() : BigDecimal.ZERO);
        m.put("unapplied", money(unapplied));
        m.put("obligations", obligations);
        m.put("movements", moves);
        m.put("payments", ps);
        return m;
    }

    // ------------------------------------------------------------------ HU-13 registrar pago

    /*
     * READ_COMMITTED + bloqueo del procedimiento: dos cobros simultáneos del mismo procedimiento no pueden
     * superar entre ambos su saldo pendiente.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> registerPayment(PaymentRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        Patient p = patient(r.patientId());
        BigDecimal amount = r.amount() == null ? null : money(r.amount());
        if (amount == null || amount.signum() <= 0) e.put("amount", "Ingresa un importe mayor a cero.");
        else if (amount.compareTo(new BigDecimal("50000")) > 0) e.put("amount", "El importe supera el máximo permitido por cobro (S/ 50 000).");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !METHODS.contains(method)) e.put("method", "Selecciona el medio de pago.");
        String reference = clean(r.reference());
        if (method != null && !"EFECTIVO".equals(method) && METHODS.contains(method) && reference == null) {
            e.put("reference", "TARJETA".equals(method) ? "Ingresa el número de voucher." : "Ingresa el número de operación.");
        }
        if (reference != null && reference.length() > 40) e.put("reference", "Máximo 40 caracteres.");
        if (!e.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del cobro.", e);

        String concept = "A cuenta";
        if (r.treatmentItemId() != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select ti.id, ti.price, tp.patient_id, tp.status as plan_status, ti.status, pr.name
                      from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id
                      join procedures pr on pr.id = ti.procedure_id
                     where ti.id = ? for update
                    """, r.treatmentItemId());
            if (rows.isEmpty()) throw ApiException.notFound("El procedimiento a cobrar");
            Map<String, Object> it = rows.get(0);
            if (!p.id.equals(((Number) it.get("patient_id")).longValue())) {
                throw ApiException.badRequest("ITEM_AJENO", "El procedimiento no pertenece al paciente seleccionado.");
            }
            if ("ANULADO".equals(it.get("plan_status")) && !"REALIZADO".equals(it.get("status"))) {
                throw ApiException.badRequest("PLAN_ANULADO", "El plan de ese procedimiento está anulado.");
            }
            BigDecimal pending = money((BigDecimal) it.get("price")).subtract(money(payments.paidForItem(r.treatmentItemId())));
            if (pending.signum() <= 0) {
                throw ApiException.conflict("ITEM_PAGADO", "\"" + it.get("name") + "\" ya está pagado por completo.");
            }
            if (amount.compareTo(pending) > 0) {
                throw ApiException.conflict("IMPORTE_EXCEDE", "El importe supera lo pendiente de \"" + it.get("name") + "\" (S/ "
                        + pending.toPlainString() + "). Registra la diferencia como pago a cuenta.",
                        Map.of("amount", "Máximo S/ " + pending.toPlainString() + " para este procedimiento."));
            }
            concept = (String) it.get("name");
        }

        Payment pay = new Payment();
        pay.patientId = p.id;
        pay.treatmentItemId = r.treatmentItemId();
        pay.amount = amount;
        pay.method = method;
        pay.reference = reference;
        pay.notes = clean(r.notes());
        pay.paidAt = Dates.now();
        pay.cashier = CurrentUser.usernameOrSystem();
        pay.status = "VALIDO";
        payments.save(pay);
        audit.record("PAGO", pay.id, "REGISTRAR", p.getFullName() + " · S/ " + amount.toPlainString() + " · "
                + METHOD_LABEL.get(method) + " · " + concept);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("payment", pay);
        m.put("concept", concept);
        m.put("account", balance(p.id));
        return m;
    }

    @Transactional
    public Payment voidPayment(Long id, String reason) {
        Payment pay = payments.findById(id).orElseThrow(() -> ApiException.notFound("El pago"));
        if (!"VALIDO".equals(pay.status)) throw ApiException.conflict("PAGO_ANULADO", "El pago ya fue anulado.");
        if (reason == null || reason.trim().length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación.");
        Long settled = jdbc.queryForObject("""
                select count(*) from commission_lines cl join commission_rules r on r.id = cl.rule_id
                 where cl.treatment_item_id = ? and r.base = 'COBRADO'
                """, Long.class, pay.treatmentItemId == null ? -1L : pay.treatmentItemId);
        if (settled != null && settled > 0) {
            throw ApiException.conflict("PAGO_LIQUIDADO", "Este cobro ya sirvió de base para una comisión liquidada; anula primero esa liquidación.");
        }
        pay.status = "ANULADO";
        pay.voidReason = reason.trim();
        pay.voidedAt = Dates.now();
        pay.voidedBy = CurrentUser.usernameOrSystem();
        payments.save(pay);
        audit.record("PAGO", pay.id, "ANULAR", "S/ " + pay.amount.toPlainString() + " · " + pay.voidReason);
        return pay;
    }

    // ------------------------------------------------------------------ HU-15 egresos

    @Transactional
    public Expense registerExpense(ExpenseRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate date = r.expenseDate() == null ? Dates.today() : r.expenseDate();
        if (date.isAfter(Dates.today())) e.put("expenseDate", "La fecha no puede ser futura.");
        if (date.isBefore(Dates.today().minusDays(62))) e.put("expenseDate", "Solo se pueden registrar egresos de los últimos dos meses.");
        String category = r.category() == null ? null : r.category().trim().toUpperCase();
        if (category == null || !EXPENSE_CATEGORIES.contains(category)) e.put("category", "Selecciona la categoría.");
        String concept = clean(r.concept());
        if (concept == null || concept.length() < 4) e.put("concept", "Describe el egreso (mínimo 4 caracteres).");
        else if (concept.length() > 200) e.put("concept", "Máximo 200 caracteres.");
        BigDecimal amount = r.amount() == null ? null : money(r.amount());
        if (amount == null || amount.signum() <= 0) e.put("amount", "Ingresa un importe mayor a cero.");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !METHODS.contains(method)) e.put("method", "Selecciona cómo se pagó.");
        if (!e.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del egreso.", e);
        Expense x = new Expense();
        x.expenseDate = date;
        x.category = category;
        x.concept = concept;
        x.amount = amount;
        x.method = method;
        x.reference = clean(r.reference());
        x.supplier = clean(r.supplier());
        x.status = "VALIDO";
        x.createdAt = Dates.now();
        x.createdBy = CurrentUser.usernameOrSystem();
        expenses.save(x);
        audit.record("EGRESO", x.id, "REGISTRAR", concept + " · S/ " + amount.toPlainString());
        return x;
    }

    @Transactional
    public Expense voidExpense(Long id, String reason) {
        Expense x = expenses.findById(id).orElseThrow(() -> ApiException.notFound("El egreso"));
        if (!"VALIDO".equals(x.status)) throw ApiException.conflict("EGRESO_ANULADO", "El egreso ya fue anulado.");
        if (reason == null || reason.trim().length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación.");
        x.status = "ANULADO";
        x.voidReason = reason.trim();
        expenses.save(x);
        audit.record("EGRESO", x.id, "ANULAR", x.concept + " · " + x.voidReason);
        return x;
    }

    // ------------------------------------------------------------------ HU-15 flujo de caja por período

    @Transactional(readOnly = true)
    public Map<String, Object> report(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("PERIODO", "Indica el período a consultar.");
        if (to.isBefore(from)) throw ApiException.field("to", "La fecha final no puede ser anterior a la inicial.");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.field("to", "El período no puede superar un año.");

        List<Payment> ps = payments.findInRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<Expense> xs = expenses.findInRange(from, to);
        Map<Long, String> names = new HashMap<>();
        Set<Long> pids = new HashSet<>();
        ps.forEach(p -> pids.add(p.patientId));
        patients.findAllById(pids).forEach(p -> names.put(p.id, p.getFullName()));
        Map<Long, String> items = new HashMap<>();
        Set<Long> itemIds = new HashSet<>();
        ps.forEach(p -> { if (p.treatmentItemId != null) itemIds.add(p.treatmentItemId); });
        if (!itemIds.isEmpty()) {
            String in = String.join(",", Collections.nCopies(itemIds.size(), "?"));
            jdbc.query("select ti.id, pr.name from treatment_items ti join procedures pr on pr.id = ti.procedure_id where ti.id in (" + in + ")",
                    rs -> { items.put(rs.getLong(1), rs.getString(2)); }, itemIds.toArray());
        }

        BigDecimal income = BigDecimal.ZERO, outgo = BigDecimal.ZERO;
        Map<String, BigDecimal[]> byMethod = new LinkedHashMap<>();
        for (String mt : METHODS) byMethod.put(mt, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        TreeMap<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();

        List<Map<String, Object>> detail = new ArrayList<>();
        int voided = 0;
        for (Payment p : ps) {
            if (!"VALIDO".equals(p.status)) { voided++; continue; }
            income = income.add(p.amount);
            byMethod.get(p.method)[0] = byMethod.get(p.method)[0].add(p.amount);
            byDay.get(p.paidAt.toLocalDate())[0] = byDay.get(p.paidAt.toLocalDate())[0].add(p.amount);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", "INGRESO");
            d.put("id", p.id);
            d.put("date", p.paidAt);
            d.put("concept", p.treatmentItemId == null ? "Pago a cuenta" : items.getOrDefault(p.treatmentItemId, "Procedimiento"));
            d.put("party", names.get(p.patientId));
            d.put("patientId", p.patientId);
            d.put("method", p.method);
            d.put("reference", p.reference);
            d.put("amount", p.amount);
            d.put("user", p.cashier);
            detail.add(d);
        }
        for (Expense x : xs) {
            if (!"VALIDO".equals(x.status)) { voided++; continue; }
            outgo = outgo.add(x.amount);
            byMethod.get(x.method)[1] = byMethod.get(x.method)[1].add(x.amount);
            byDay.get(x.expenseDate)[1] = byDay.get(x.expenseDate)[1].add(x.amount);
            byCategory.merge(x.category, x.amount, BigDecimal::add);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", "EGRESO");
            d.put("id", x.id);
            d.put("date", x.expenseDate.atStartOfDay());
            d.put("concept", x.concept);
            d.put("party", x.supplier);
            d.put("category", x.category);
            d.put("method", x.method);
            d.put("reference", x.reference);
            d.put("amount", x.amount);
            d.put("user", x.createdBy);
            detail.add(d);
        }
        detail.sort((a, b) -> ((LocalDateTime) b.get("date")).compareTo((LocalDateTime) a.get("date")));

        List<Map<String, Object>> methods = new ArrayList<>();
        byMethod.forEach((k, v) -> {
            if (v[0].signum() == 0 && v[1].signum() == 0) return;
            methods.add(Map.of("method", k, "label", METHOD_LABEL.get(k), "income", money(v[0]), "outgo", money(v[1]),
                    "net", money(v[0].subtract(v[1]))));
        });
        List<Map<String, Object>> days = new ArrayList<>();
        byDay.forEach((k, v) -> days.add(Map.of("date", k, "income", money(v[0]), "outgo", money(v[1]))));
        List<Map<String, Object>> categories = new ArrayList<>();
        byCategory.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(en -> categories.add(Map.of("category", en.getKey(), "amount", money(en.getValue()))));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("income", money(income));
        m.put("outgo", money(outgo));
        m.put("net", money(income.subtract(outgo)));
        m.put("incomeCount", detail.stream().filter(d -> "INGRESO".equals(d.get("type"))).count());
        m.put("outgoCount", detail.stream().filter(d -> "EGRESO".equals(d.get("type"))).count());
        m.put("voided", voided);
        m.put("byMethod", methods);
        m.put("byDay", days);
        m.put("byCategory", categories);
        m.put("detail", detail);
        return m;
    }
}
