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
    private static final String ANULADO = "ANULADO";
    private static final String EFECTIVO = "EFECTIVO";
    private static final String EGRESO = "EGRESO";
    private static final String TARJETA = "TARJETA";
    private static final String VALIDO = "VALIDO";
    private static final String AMOUNT = "amount";
    private static final String CATEGORY = "category";
    private static final String CHARGE = "charge";
    private static final String CONCEPT = "concept";
    private static final String CREDIT = "credit";
    private static final String DETAIL = "detail";
    private static final String INCOME = "income";
    private static final String METHOD = "method";
    private static final String OUTGO = "outgo";
    private static final String PRICE = "price";
    private static final String PROCEDURE = "procedure";
    private static final String REFERENCE = "reference";
    private static final String STATUS = "status";
    private static final String TOOTH = "tooth";


    public static final List<String> METHODS = List.of(EFECTIVO, TARJETA, "YAPE", "PLIN", "TRANSFERENCIA");
    public static final Map<String, String> METHOD_LABEL = Map.of(EFECTIVO, "Efectivo", TARJETA, "Tarjeta",
            "YAPE", "Yape", "PLIN", "Plin", "TRANSFERENCIA", "Transferencia");
    public static final List<String> EXPENSE_CATEGORIES = List.of("INSUMOS", "LABORATORIO", "HONORARIOS", "ALQUILER", "SERVICIOS",
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
        return obligationsInternal(patientId);
    }

    private List<Map<String, Object>> obligationsInternal(Long patientId) {
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
            BigDecimal price = money(rs.getBigDecimal(PRICE));
            BigDecimal paid = money(rs.getBigDecimal("paid"));
            m.put("treatmentItemId", rs.getLong("id"));
            m.put(PROCEDURE, rs.getString("name"));
            m.put(CATEGORY, rs.getString(CATEGORY));
            m.put(TOOTH, rs.getObject(TOOTH));
            m.put("planId", rs.getLong("plan_id"));
            m.put("planTitle", rs.getString("title"));
            m.put(STATUS, rs.getString(STATUS));
            Timestamp done = rs.getTimestamp("done_at");
            m.put("doneAt", done == null ? null : done.toLocalDateTime());
            m.put(PRICE, price);
            m.put("paid", paid);
            m.put("pending", price.subtract(paid).max(BigDecimal.ZERO));
            return m;
        }, patientId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> balance(Long patientId) {
        return balanceInternal(patientId);
    }

    private Map<String, Object> balanceInternal(Long patientId) {
        Patient p = patient(patientId);
        List<Map<String, Object>> obligations = obligationsInternal(patientId);
        List<Payment> ps = payments.findByPatientIdOrderByPaidAtDesc(patientId);
        Map<Long, String> itemNames = new HashMap<>();
        for (Map<String, Object> o : obligations) itemNames.put((Long) o.get("treatmentItemId"), (String) o.get(PROCEDURE));

        BigDecimal charges = BigDecimal.ZERO;
        List<Map<String, Object>> moves = new ArrayList<>();
        for (Map<String, Object> o : obligations) {
            if (!"REALIZADO".equals(o.get(STATUS))) continue;
            charges = charges.add((BigDecimal) o.get(PRICE));
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("date", o.get("doneAt"));
            mv.put("type", "CARGO");
            mv.put(CONCEPT, o.get(PROCEDURE) + (o.get(TOOTH) == null ? "" : " · pieza " + o.get(TOOTH)));
            mv.put(DETAIL, o.get("planTitle"));
            mv.put(CHARGE, o.get(PRICE));
            mv.put(CREDIT, BigDecimal.ZERO);
            moves.add(mv);
        }
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal unapplied = BigDecimal.ZERO;
        for (Payment pay : ps) {
            if (!VALIDO.equals(pay.status)) continue;
            credits = credits.add(pay.amount);
            if (pay.treatmentItemId == null) unapplied = unapplied.add(pay.amount);
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("date", pay.paidAt);
            mv.put("type", "ABONO");
            mv.put(CONCEPT, "Pago " + METHOD_LABEL.getOrDefault(pay.method, pay.method).toLowerCase()
                    + (pay.reference == null ? "" : " · op. " + pay.reference));
            mv.put(DETAIL, pay.treatmentItemId == null ? "A cuenta" : itemNames.getOrDefault(pay.treatmentItemId, "Procedimiento"));
            mv.put(CHARGE, BigDecimal.ZERO);
            mv.put(CREDIT, pay.amount);
            mv.put("paymentId", pay.id);
            moves.add(mv);
        }
        moves.sort(Comparator.comparing(m -> (LocalDateTime) m.get("date"), Comparator.nullsLast(Comparator.naturalOrder())));
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> mv : moves) {
            running = running.add((BigDecimal) mv.get(CHARGE)).subtract((BigDecimal) mv.get(CREDIT));
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
        if (amount == null || amount.signum() <= 0) e.put(AMOUNT, "Ingresa un importe mayor a cero.");
        else if (amount.compareTo(new BigDecimal("50000")) > 0) e.put(AMOUNT, "El importe supera el máximo permitido por cobro (S/ 50 000).");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !METHODS.contains(method)) e.put(METHOD, "Selecciona el medio de pago.");
        String reference = clean(r.reference());
        if (method != null && !EFECTIVO.equals(method) && METHODS.contains(method) && reference == null) {
            e.put(REFERENCE, TARJETA.equals(method) ? "Ingresa el número de voucher." : "Ingresa el número de operación.");
        }
        if (reference != null && reference.length() > 40) e.put(REFERENCE, "Máximo 40 caracteres.");
        if (!e.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del cobro.", e);
        java.util.Objects.requireNonNull(amount);

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
            if (ANULADO.equals(it.get("plan_status")) && !"REALIZADO".equals(it.get(STATUS))) {
                throw ApiException.badRequest("PLAN_ANULADO", "El plan de ese procedimiento está anulado.");
            }
            BigDecimal pending = money((BigDecimal) it.get(PRICE)).subtract(money(payments.paidForItem(r.treatmentItemId())));
            if (pending.signum() <= 0) {
                throw ApiException.conflict("ITEM_PAGADO", "\"" + it.get("name") + "\" ya está pagado por completo.");
            }
            if (amount.compareTo(pending) > 0) {
                throw ApiException.conflict("IMPORTE_EXCEDE", "El importe supera lo pendiente de \"" + it.get("name") + "\" (S/ "
                        + pending.toPlainString() + "). Registra la diferencia como pago a cuenta.",
                        Map.of(AMOUNT, "Máximo S/ " + pending.toPlainString() + " para este procedimiento."));
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
        pay.status = VALIDO;
        pay.appointmentId = pe.clinicacardenas.agenda.AttentionLink.current(jdbc, pay.patientId);
        payments.save(pay);
        audit.register("PAGO", pay.id, "REGISTRAR", p.getFullName() + " · S/ " + amount.toPlainString() + " · "
                + METHOD_LABEL.get(method) + " · " + concept);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("payment", pay);
        m.put(CONCEPT, concept);
        m.put("account", balanceInternal(p.id));
        return m;
    }

    @Transactional
    public Payment voidPayment(Long id, String reason) {
        Payment pay = payments.findById(id).orElseThrow(() -> ApiException.notFound("El pago"));
        if (!VALIDO.equals(pay.status)) throw ApiException.conflict("PAGO_ANULADO", "El pago ya fue anulado.");
        // v2.18: un cobro con comprobante aceptado no se anula a escondidas: primero se anula el comprobante.
        List<String> inv = jdbc.queryForList("""
                select concat(i.series, '-', i.number) from payments p
                  join invoices i on i.id = p.invoice_id
                    or i.id = (select ti.invoice_id from treatment_items ti where ti.id = p.treatment_item_id)
                 where p.id = ? and i.status not in ('RECHAZADO','ANULADO') limit 1""", String.class, id);
        if (!inv.isEmpty()) throw ApiException.conflict("PAGO_CON_COMPROBANTE",
                "Este cobro tiene el comprobante " + inv.get(0) + ". Anula primero el comprobante (nota de crédito) y luego el cobro.");
        if (reason == null || reason.trim().length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación.");
        Long settled = jdbc.queryForObject("""
                select count(*) from commission_lines cl join commission_rules r on r.id = cl.rule_id
                 where cl.treatment_item_id = ? and r.base = 'COBRADO'
                """, Long.class, pay.treatmentItemId == null ? -1L : pay.treatmentItemId);
        if (settled != null && settled > 0) {
            throw ApiException.conflict("PAGO_LIQUIDADO", "Este cobro ya sirvió de base para una comisión liquidada; anula primero esa liquidación.");
        }
        pay.status = ANULADO;
        pay.voidReason = reason.trim();
        pay.voidedAt = Dates.now();
        pay.voidedBy = CurrentUser.usernameOrSystem();
        payments.save(pay);
        audit.register("PAGO", pay.id, "ANULAR", "S/ " + pay.amount.toPlainString() + " · " + pay.voidReason);
        return pay;
    }

    // ------------------------------------------------------------------ HU-15 egresos

    @Transactional
    public Expense registerExpense(ExpenseRequest r) {
        Expense x = registerPreview(r);
        x.status = VALIDO;
        x.createdAt = Dates.now();
        x.createdBy = CurrentUser.usernameOrSystem();
        expenses.save(x);
        audit.register(EGRESO, x.id, "REGISTRAR", x.concept + " · S/ " + x.amount.toPlainString());
        return x;
    }

    /** Valida los datos del egreso y devuelve un objeto sin guardar. */
    private Expense registerPreview(ExpenseRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate date = r.expenseDate() == null ? Dates.today() : r.expenseDate();
        if (date.isAfter(Dates.today())) e.put("expenseDate", "La fecha no puede ser futura.");
        if (date.isBefore(Dates.today().minusDays(62))) e.put("expenseDate", "Solo se pueden registrar egresos de los últimos dos meses.");
        String category = r.category() == null ? null : r.category().trim().toUpperCase();
        if (category == null || !EXPENSE_CATEGORIES.contains(category)) e.put(CATEGORY, "Selecciona la categoría.");
        String concept = clean(r.concept());
        if (concept == null || concept.length() < 4) e.put(CONCEPT, "Describe el egreso (mínimo 4 caracteres).");
        else if (concept.length() > 200) e.put(CONCEPT, "Máximo 200 caracteres.");
        BigDecimal amount = r.amount() == null ? null : money(r.amount());
        if (amount == null || amount.signum() <= 0) e.put(AMOUNT, "Ingresa un importe mayor a cero.");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !METHODS.contains(method)) e.put(METHOD, "Selecciona cómo se pagó.");
        if (!e.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del egreso.", e);
        java.util.Objects.requireNonNull(amount);
        Expense x = new Expense();
        x.expenseDate = date;
        x.category = category;
        x.concept = concept;
        x.amount = amount;
        x.method = method;
        x.reference = clean(r.reference());
        x.supplier = clean(r.supplier());
        return x;
    }

    /**
     * Corrige un egreso válido de un día con caja abierta. No se editan los egresos generados por otros módulos
     * (compras de inventario, pagos a laboratorio o a odontólogos): se corrigen desde su módulo o se anulan.
     */
    public Expense expense(Long id) {
        return expenses.findById(id).orElseThrow(() -> ApiException.notFound("El egreso"));
    }

    @Transactional
    public Expense updateExpense(Long id, ExpenseRequest r) {
        Expense x = expenses.findById(id).orElseThrow(() -> ApiException.notFound("El egreso"));
        if (!VALIDO.equals(x.status)) throw ApiException.conflict("EGRESO_ANULADO", "El egreso está anulado; no se puede editar.");
        Integer linked = jdbc.queryForObject("""
                select (select count(*) from inventory_purchases where expense_id = ?)
                     + (select count(*) from lab_order_payments where expense_id = ?)
                     + (select count(*) from commission_payouts where expense_id = ?)""", Integer.class, id, id, id);
        if (linked != null && linked > 0) throw ApiException.conflict("EGRESO_VINCULADO",
                "Este egreso se generó desde inventario, laboratorio o comisiones: corrígelo allí o anúlalo.");
        Expense tmp = registerPreview(r);
        x.expenseDate = tmp.expenseDate;
        x.category = tmp.category;
        x.concept = tmp.concept;
        x.amount = tmp.amount;
        x.method = tmp.method;
        x.reference = tmp.reference;
        x.supplier = tmp.supplier;
        expenses.save(x);
        audit.register(EGRESO, x.id, "EDITAR", x.concept + " · S/ " + x.amount.toPlainString());
        return x;
    }

    @Transactional
    public Expense voidExpense(Long id, String reason) {
        Expense x = expenses.findById(id).orElseThrow(() -> ApiException.notFound("El egreso"));
        if (!VALIDO.equals(x.status)) throw ApiException.conflict("EGRESO_ANULADO", "El egreso ya fue anulado.");
        if (reason == null || reason.trim().length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación.");
        x.status = ANULADO;
        x.voidReason = reason.trim();
        expenses.save(x);
        audit.register(EGRESO, x.id, "ANULAR", x.concept + " · " + x.voidReason);
        // Si el egreso era el pago de una liquidación de comisiones, la liquidación vuelve a tener ese saldo pendiente.
        List<Map<String, Object>> payout = jdbc.queryForList("select id, settlement_id, amount from commission_payouts where expense_id = ?", x.id);
        if (!payout.isEmpty()) {
            Map<String, Object> po = payout.get(0);
            jdbc.update("delete from commission_payouts where id = ?", po.get("id"));
            jdbc.update("""
                    update commission_settlements
                       set status = case when paid_amount - ? <= 0 then 'PENDIENTE' else 'PARCIAL' end,
                           paid_amount = greatest(paid_amount - ?, 0)
                     where id = ? and status <> 'ANULADA'
                    """, po.get("amount"), po.get("amount"), po.get("settlement_id"));
            // (MySQL asigna de izquierda a derecha: el estado se calcula antes de restar el importe.)
            audit.register("LIQUIDACION", ((Number) po.get("settlement_id")).longValue(), "PAGO_ANULADO",
                    "Pago de S/ " + po.get("amount") + " revertido al anular el egreso " + x.id);
        }
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
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> items.put(rs.getLong(1), rs.getString(2)), itemIds.toArray());
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal outgo = BigDecimal.ZERO;
        Map<String, BigDecimal[]> byMethod = new LinkedHashMap<>();
        for (String mt : METHODS) byMethod.put(mt, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        TreeMap<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();

        List<Map<String, Object>> detail = new ArrayList<>();
        int voided = 0;
        for (Payment p : ps) {
            if (!VALIDO.equals(p.status)) { voided++; continue; }
            income = income.add(p.amount);
            byMethod.get(p.method)[0] = byMethod.get(p.method)[0].add(p.amount);
            byDay.get(p.paidAt.toLocalDate())[0] = byDay.get(p.paidAt.toLocalDate())[0].add(p.amount);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", "INGRESO");
            d.put("id", p.id);
            d.put("date", p.paidAt);
            d.put(CONCEPT, p.treatmentItemId == null ? "Pago a cuenta" : items.getOrDefault(p.treatmentItemId, "Procedimiento"));
            d.put("party", names.get(p.patientId));
            d.put("patientId", p.patientId);
            d.put(METHOD, p.method);
            d.put(REFERENCE, p.reference);
            d.put(AMOUNT, p.amount);
            d.put("user", p.cashier);
            detail.add(d);
        }
        for (Expense x : xs) {
            if (!VALIDO.equals(x.status)) { voided++; continue; }
            outgo = outgo.add(x.amount);
            byMethod.get(x.method)[1] = byMethod.get(x.method)[1].add(x.amount);
            byDay.get(x.expenseDate)[1] = byDay.get(x.expenseDate)[1].add(x.amount);
            byCategory.merge(x.category, x.amount, BigDecimal::add);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", EGRESO);
            d.put("id", x.id);
            d.put("date", x.expenseDate.atStartOfDay());
            d.put(CONCEPT, x.concept);
            d.put("party", x.supplier);
            d.put(CATEGORY, x.category);
            d.put(METHOD, x.method);
            d.put(REFERENCE, x.reference);
            d.put(AMOUNT, x.amount);
            d.put("user", x.createdBy);
            detail.add(d);
        }
        detail.sort((a, b) -> ((LocalDateTime) b.get("date")).compareTo((LocalDateTime) a.get("date")));

        List<Map<String, Object>> methods = new ArrayList<>();
        byMethod.forEach((k, v) -> {
            if (v[0].signum() == 0 && v[1].signum() == 0) return;
            methods.add(Map.of(METHOD, k, "label", METHOD_LABEL.get(k), INCOME, money(v[0]), OUTGO, money(v[1]),
                    "net", money(v[0].subtract(v[1]))));
        });
        List<Map<String, Object>> days = new ArrayList<>();
        byDay.forEach((k, v) -> days.add(Map.of("date", k, INCOME, money(v[0]), OUTGO, money(v[1]))));
        List<Map<String, Object>> categories = new ArrayList<>();
        byCategory.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(en -> categories.add(Map.of(CATEGORY, en.getKey(), AMOUNT, money(en.getValue()))));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put(INCOME, money(income));
        m.put(OUTGO, money(outgo));
        m.put("net", money(income.subtract(outgo)));
        m.put("incomeCount", detail.stream().filter(d -> "INGRESO".equals(d.get("type"))).count());
        m.put("outgoCount", detail.stream().filter(d -> EGRESO.equals(d.get("type"))).count());
        m.put("voided", voided);
        m.put("byMethod", methods);
        m.put("byDay", days);
        m.put("byCategory", categories);
        m.put(DETAIL, detail);
        return m;
    }
}
