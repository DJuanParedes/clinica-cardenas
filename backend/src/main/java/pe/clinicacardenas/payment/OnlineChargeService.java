package pe.clinicacardenas.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.clinicacardenas.assistant.ConversationService;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cobros en línea con Mercado Pago (Checkout Pro): tarjeta de crédito o débito, Yape y saldo de Mercado Pago.
 *
 * Flujo: se crea un link de pago (preferencia) por un monto → el paciente paga en la página segura de Mercado Pago
 * (el sistema nunca ve la tarjeta) → Mercado Pago avisa por webhook y, además, la pantalla consulta el estado →
 * el sistema verifica el pago directamente con la API de Mercado Pago y, si está aprobado, lo registra en caja
 * (una sola vez, aunque lleguen varios avisos).
 */
@Service
public class OnlineChargeService {
    private static final String COBRO_EN_LINEA = "COBRO_EN_LINEA";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String AMOUNT = "amount";
    private static final String APPROVED = "approved";
    private static final String DESCRIPTION = "description";
    private static final String EXPIRES_AT = "expires_at";
    private static final String PATIENT_ID = "patient_id";
    private static final String STATUS = "status";
    private static final String TREATMENT_ITEM_ID = "treatment_item_id";


    private static final Logger log = LoggerFactory.getLogger(OnlineChargeService.class);
    private static final int MINUTES_VALID = 60;
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final MercadoPagoClient mp;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CashService cash;
    private final PatientRepository patients;
    private final ConversationService conversations;
    private final AuditService audit;
    private final String publicUrl;
    private final String mode;
    private final pe.clinicacardenas.billing.PaymentInvoiceService invoices;

    public OnlineChargeService(MercadoPagoClient mp, JdbcTemplate jdbc, TransactionTemplate tx, CashService cash,
                               PatientRepository patients, ConversationService conversations, AuditService audit,
                               @Value("${app.public-url:}") String publicUrl,
                               @Value("${app.payments.mode:simulado}") String mode,
                               pe.clinicacardenas.billing.PaymentInvoiceService invoices) {
        this.invoices = invoices;
        this.mp = mp;
        this.jdbc = jdbc;
        this.tx = tx;
        this.cash = cash;
        this.patients = patients;
        this.conversations = conversations;
        this.audit = audit;
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
        this.mode = mode == null ? "simulado" : mode.trim().toLowerCase();
    }

    /** Mercado Pago activo: PAGOS_MODO=mercadopago y un Access Token. */
    public boolean enabled() {
        return "mercadopago".equals(mode) && mp.configured();
    }

    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", enabled() ? "MERCADOPAGO" : "NINGUNO");
        m.put("enabled", enabled());
        m.put("testMode", enabled() && mp.testMode());
        m.put("methods", List.of("TARJETA", "YAPE"));
        String modeMessage = mp.testMode() ? "Mercado Pago en modo de pruebas: usa tarjetas de prueba, no se cobra dinero real." : "Mercado Pago activo: los cobros son reales.";
        m.put("message", enabled() ? modeMessage : "Para cobrar en línea configura PAGOS_MODO=mercadopago y MP_ACCESS_TOKEN en el servidor.");
        return m;
    }

    public record CreateRequest(Long patientId, Long treatmentItemId, BigDecimal amount, String description) {}

    // ------------------------------------------------------------------ crear el link de pago

    public Map<String, Object> create(CreateRequest r, String source) {
        if (!enabled()) throw ApiException.conflict("PAGOS_NO_CONFIGURADOS", (String) config().get("message"));
        Patient p = patients.findById(r.patientId() == null ? -1L : r.patientId()).orElseThrow(() -> ApiException.notFound("El paciente"));
        BigDecimal amount = r.amount() == null ? null : r.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount == null || amount.signum() <= 0) throw ApiException.field(AMOUNT, "Ingresa un importe mayor a cero.");
        if (amount.compareTo(new BigDecimal("50000")) > 0) throw ApiException.field(AMOUNT, "El importe supera el máximo permitido (S/ 50 000).");
        String concept = "Pago a cuenta";
        if (r.treatmentItemId() != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select pr.name, ti.price, tp.patient_id,
                           ti.price - coalesce((select sum(py.amount) from payments py
                                                 where py.treatment_item_id = ti.id and py.status = 'VALIDO'), 0) as pending
                      from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id
                      join procedures pr on pr.id = ti.procedure_id
                     where ti.id = ?
                    """, r.treatmentItemId());
            if (rows.isEmpty() || ((Number) rows.get(0).get(PATIENT_ID)).longValue() != p.id) {
                throw ApiException.badRequest("ITEM_AJENO", "El procedimiento no pertenece al paciente.");
            }
            BigDecimal pending = (BigDecimal) rows.get(0).get("pending");
            if (pending.signum() <= 0) throw ApiException.conflict("ITEM_PAGADO", "Ese procedimiento ya está pagado.");
            if (amount.compareTo(pending) > 0) throw ApiException.field(AMOUNT, "Máximo S/ " + pending.setScale(2, RoundingMode.HALF_UP).toPlainString() + " para este procedimiento.");
            concept = (String) rows.get(0).get("name");
        }
        String description = r.description() != null && !r.description().isBlank() ? r.description().trim() : concept;
        if (description.length() > 150) description = description.substring(0, 150);
        LocalDateTime now = Dates.now();
        LocalDateTime expires = now.plusMinutes(MINUTES_VALID);
        String createdBy = "APP".equals(source) ? "paciente:" + p.id : CurrentUser.usernameOrSystem();

        GeneratedKeyHolder key = new GeneratedKeyHolder();
        final String desc = description;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into online_charges (provider, source, patient_id, treatment_item_id, amount, description, status,
                                                expires_at, created_at, created_by)
                    values ('MERCADOPAGO', ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, source);
            ps.setLong(2, p.id);
            if (r.treatmentItemId() == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, r.treatmentItemId());
            ps.setBigDecimal(4, amount);
            ps.setString(5, desc);
            ps.setTimestamp(6, Timestamp.valueOf(expires));
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setString(8, createdBy);
            return ps;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "CC-" + id);
        item.put("title", "Clínica Dental Cárdenas · " + desc);
        item.put("quantity", 1);
        item.put("currency_id", "PEN");
        item.put("unit_price", amount);
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("name", p.firstName);
        payer.put("surname", p.lastName);
        // En modo de pruebas no se envía el correo real del paciente: Mercado Pago rechaza mezclar un vendedor de prueba
        // con un comprador real ("Una de las partes con la que intentas hacer el pago es de prueba").
        if (!mp.testMode() && p.email != null && !p.email.isBlank()) payer.put("email", p.email);
        if (!mp.testMode() && "DNI".equals(p.docType)) payer.put("identification", Map.of("type", "DNI", "number", p.docNumber));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("payer", payer);
        body.put("external_reference", reference(id));
        body.put("statement_descriptor", "CLINICA CARDENAS");
        body.put("expires", true);
        body.put("expiration_date_from", MP_DATE.format(now.atZone(ZoneId.of("America/Lima"))));
        body.put("expiration_date_to", MP_DATE.format(expires.atZone(ZoneId.of("America/Lima"))));
        body.put("payment_methods", Map.of("installments", 1, "excluded_payment_types", List.of(Map.of("id", "ticket"))));
        if (publicUrl.startsWith("https://")) {
            body.put("notification_url", publicUrl + "/api/public/mercadopago/webhook?source_news=webhooks");
            String back = publicUrl + "/pago/resultado?ref=" + reference(id);
            body.put("back_urls", Map.of("success", back, "pending", back, "failure", back));
            body.put("auto_return", APPROVED);
        }
        JsonNode pref;
        try {
            pref = mp.createPreference(body);
        } catch (RuntimeException e) {
            jdbc.update("update online_charges set status = 'CANCELADO', mp_status_detail = 'error_al_crear', updated_at = ? where id = ?", Dates.now(), id);
            throw e;
        }
        // El link sandbox solo sirve con las llaves antiguas (TEST-…). Con las credenciales de prueba nuevas (APP_USR-…)
        // Mercado Pago pide el init_point normal: el ambiente de prueba lo da la credencial, no el link.
        String url = mp.legacyTestKeys() && pref.hasNonNull("sandbox_init_point") ? pref.get("sandbox_init_point").asText() : pref.path("init_point").asText();
        jdbc.update("update online_charges set preference_id = ?, checkout_url = ?, updated_at = ? where id = ?",
                pref.path("id").asText(), url, Dates.now(), id);
        audit.register(COBRO_EN_LINEA, id, "CREAR", p.getFullName() + " · S/ " + amount.toPlainString() + " · " + desc);
        return view(id);
    }

    /** El paciente paga desde la app o el portal: hasta su saldo pendiente. */
    public Map<String, Object> createForPatient(long patientId, BigDecimal amount) {
        Object bal = cash.balance(patientId).get("balance");
        BigDecimal balance = bal instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(bal));
        if (balance.signum() <= 0) throw ApiException.conflict("SIN_DEUDA", "No tienes saldo pendiente.");
        if (amount != null && amount.compareTo(balance) > 0) {
            throw ApiException.field(AMOUNT, "El monto supera tu saldo pendiente (S/ " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        return create(new CreateRequest(patientId, null, amount == null ? balance : amount, "Pago de tratamiento dental"), "APP");
    }

    // ------------------------------------------------------------------ estado

    /** Estado del cobro; si sigue pendiente, pregunta a Mercado Pago (funciona aunque el webhook no llegue). */
    public Map<String, Object> status(long id, Long onlyPatientId) {
        Map<String, Object> c = row(id);
        if (onlyPatientId != null && ((Number) c.get(PATIENT_ID)).longValue() != onlyPatientId) throw ApiException.notFound("El cobro");
        if (PENDIENTE.equals(c.get(STATUS)) && enabled()) {
            try {
                JsonNode found = mp.searchByReference(reference(id)).path("results");
                JsonNode best = null;
                for (JsonNode pay : found) {
                    if (APPROVED.equals(pay.path(STATUS).asText())) { best = pay; break; }
                    if (best == null) best = pay;
                }
                if (best != null) apply(best);
            } catch (ApiException e) {
                log.warn("No se pudo consultar el cobro {} en Mercado Pago: {}", id, e.getMessage());
            }
            c = row(id);
            if (PENDIENTE.equals(c.get(STATUS)) && ((LocalDateTime) c.get(EXPIRES_AT)).isBefore(Dates.now())) {
                jdbc.update("update online_charges set status = 'VENCIDO', updated_at = ? where id = ? and status = 'PENDIENTE'", Dates.now(), id);
            }
        }
        return view(id);
    }

    public Map<String, Object> cancel(long id) {
        Map<String, Object> c = status(id, null);
        if (!PENDIENTE.equals(c.get(STATUS))) throw ApiException.conflict("COBRO_CERRADO", "Este cobro ya no está pendiente (" + c.get("statusLabel") + ").");
        jdbc.update("update online_charges set status = 'CANCELADO', updated_at = ? where id = ? and status = 'PENDIENTE'", Dates.now(), id);
        audit.register(COBRO_EN_LINEA, id, "CANCELAR", "Link de pago anulado");
        return view(id);
    }

    /** Envía el link de pago al WhatsApp del paciente. */
    public Map<String, Object> sendWhatsapp(long id) {
        Map<String, Object> c = status(id, null);
        if (!PENDIENTE.equals(c.get(STATUS))) throw ApiException.conflict("COBRO_CERRADO", "Este cobro ya no está pendiente.");
        Patient p = patients.findById(((Number) c.get("patientId")).longValue()).orElseThrow(() -> ApiException.notFound("El paciente"));
        String text = "Hola " + p.firstName + ", te enviamos el link para pagar *S/ " + ((BigDecimal) c.get(AMOUNT)).toPlainString()
                + "* (" + c.get(DESCRIPTION) + ") en Clínica Dental Cárdenas.\n\nPuedes pagar con tarjeta o Yape en la página segura de Mercado Pago:\n"
                + c.get("checkoutUrl") + "\n\nEl link vence en " + MINUTES_VALID + " minutos.";
        conversations.send(p.phone, new ConversationService.SendRequest(text));
        return Map.of("sent", true, "phone", p.phone);
    }

    public List<Map<String, Object>> recent(Long patientId) {
        List<Long> ids = patientId == null
                ? jdbc.queryForList("select id from online_charges order by id desc limit 30", Long.class)
                : jdbc.queryForList("select id from online_charges where patient_id = ? order by id desc limit 30", Long.class, patientId);
        return ids.stream().map(this::view).toList();
    }

    // ------------------------------------------------------------------ webhook

    /** Aviso de Mercado Pago. Nunca se confía en el contenido: se vuelve a consultar el pago a la API. */
    public void webhook(String type, String dataId, String xSignature, String xRequestId) {
        if (!mp.validSignature(xSignature, xRequestId, dataId)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "FIRMA_INVALIDA", "Firma de Mercado Pago no válida.");
        }
        if (dataId == null || dataId.isBlank() || (type != null && !type.startsWith("payment"))) return;
        if (!enabled()) return;
        apply(mp.payment(dataId));
    }

    /** Aplica el estado de un pago de Mercado Pago a su cobro. Idempotente. */
    void apply(JsonNode pay) {
        String ref = pay.path("external_reference").asText("");
        if (!ref.startsWith("CC-")) return;
        long id;
        try { id = Long.parseLong(ref.substring(3)); } catch (NumberFormatException e) { return; }
        String mpId = pay.path("id").asText();
        String st = pay.path(STATUS).asText();
        String detail = pay.path("status_detail").asText(null);
        String methodId = pay.path("payment_method_id").asText("");
        String typeId = pay.path("payment_type_id").asText("");
        BigDecimal paidAmount = pay.path("transaction_amount").decimalValue();
        java.util.concurrent.atomic.AtomicReference<Long> newPayment = new java.util.concurrent.atomic.AtomicReference<>();

        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select * from online_charges where id = ? for update", id);
            if (rows.isEmpty()) return;
            Map<String, Object> c = rows.get(0);
            if (c.get("payment_id") != null) return;   // ya registrado
            if (APPROVED.equals(st)) {
                if (((BigDecimal) c.get(AMOUNT)).compareTo(paidAmount) != 0) {
                    log.warn("Cobro {}: Mercado Pago aprobó S/ {} y se esperaba S/ {}", id, paidAmount, c.get(AMOUNT));
                }
                Long itemId = c.get(TREATMENT_ITEM_ID) == null ? null : ((Number) c.get(TREATMENT_ITEM_ID)).longValue();
                String method = "TARJETA";
                if ("yape".equalsIgnoreCase(methodId)) method = "YAPE";
                else if ("bank_transfer".equals(typeId) || "account_money".equals(typeId)) method = "TRANSFERENCIA";
                String reference = "MP-" + mpId;
                String notes = "Mercado Pago · " + labelMethod(methodId, typeId) + " · operación " + mpId;
                Map<String, Object> reg;
                try {
                    reg = cash.registerPayment(new CashService.PaymentRequest(((Number) c.get(PATIENT_ID)).longValue(), itemId,
                            paidAmount, method, reference, notes));
                } catch (ApiException e) {
                    // El dinero ya entró: si el procedimiento se pagó por otro lado mientras tanto, queda como pago a cuenta.
                    reg = cash.registerPayment(new CashService.PaymentRequest(((Number) c.get(PATIENT_ID)).longValue(), null,
                            paidAmount, method, reference, notes + " (a cuenta: " + e.getMessage() + ")"));
                }
                Object payment = reg.get("payment");
                Long paymentId = payment instanceof pe.clinicacardenas.cash.Payment pp ? pp.id : null;
                jdbc.update("""
                        update online_charges set status = 'APROBADO', mp_payment_id = ?, mp_status = ?, mp_status_detail = ?,
                               mp_method = ?, payment_id = ?, updated_at = ? where id = ?
                        """, mpId, st, detail, methodId, paymentId, Dates.now(), id);
                audit.register(COBRO_EN_LINEA, id, "APROBADO", "Mercado Pago " + mpId + " · S/ " + paidAmount.toPlainString());
                newPayment.set(paymentId);
            } else if ("rejected".equals(st) || "cancelled".equals(st)) {
                jdbc.update("update online_charges set mp_status = ?, mp_status_detail = ?, mp_method = ?, updated_at = ? where id = ? and status = 'PENDIENTE'",
                        st, detail, methodId, Dates.now(), id);
            } else {
                jdbc.update("update online_charges set mp_status = ?, mp_status_detail = ?, mp_method = ?, updated_at = ? where id = ? and status = 'PENDIENTE'",
                        st, detail, methodId, Dates.now(), id);
            }
        });
        // Boleta electrónica automática del pago aprobado (si falla, el pago queda registrado y se emite desde Comprobantes).
        if (newPayment.get() != null) {
            try {
                invoices.issueForPayment(newPayment.get());
            } catch (RuntimeException e) {
                log.warn("Cobro {}: no se pudo emitir la boleta automática: {}", id, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ apoyo

    static String reference(long id) {
        return "CC-" + id;
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from online_charges where id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("El cobro");
        Map<String, Object> c = rows.get(0);
        Object exp = c.get(EXPIRES_AT);
        if (exp instanceof Timestamp t) c.put(EXPIRES_AT, t.toLocalDateTime());
        return c;
    }

    private Map<String, Object> view(long id) {
        Map<String, Object> c = row(id);
        String st = (String) c.get(STATUS);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("reference", reference(id));
        m.put("patientId", c.get(PATIENT_ID));
        m.put("treatmentItemId", c.get(TREATMENT_ITEM_ID));
        m.put(AMOUNT, c.get(AMOUNT));
        m.put(DESCRIPTION, c.get(DESCRIPTION));
        m.put(STATUS, st);
        m.put("statusLabel", switch (st) {
            case "APROBADO" -> "Pagado";
            case "CANCELADO" -> "Anulado";
            case "VENCIDO" -> "Vencido";
            default -> "rejected".equals(c.get("mp_status")) ? "Rechazado, puede reintentar" : "Esperando el pago";
        });
        m.put("checkoutUrl", c.get("checkout_url"));
        m.put("mpPaymentId", c.get("mp_payment_id"));
        m.put("mpStatus", c.get("mp_status"));
        m.put("mpStatusDetail", c.get("mp_status_detail"));
        m.put("method", c.get("mp_method"));
        m.put("paymentId", c.get("payment_id"));
        if (c.get("payment_id") != null) {
            List<Map<String, Object>> inv = jdbc.queryForList(
                    "select i.id, concat(i.series, '-', lpad(i.number, 8, '0')) as num, i.status from payments p join invoices i on i.id = p.invoice_id where p.id = ?",
                    c.get("payment_id"));
            if (!inv.isEmpty()) {
                m.put("invoiceId", inv.get(0).get("id"));
                m.put("invoice", inv.get(0).get("num"));
                m.put("invoiceStatus", inv.get(0).get("status"));
            }
        }
        m.put("expiresAt", c.get(EXPIRES_AT));
        m.put("createdAt", c.get("created_at"));
        m.put("source", c.get("source"));
        m.put("testMode", mp.testMode());
        return m;
    }

    private static String labelMethod(String methodId, String typeId) {
        if ("yape".equalsIgnoreCase(methodId)) return "Yape";
        if ("account_money".equals(typeId)) return "saldo de Mercado Pago";
        String card = methodId == null || methodId.isBlank() ? "tarjeta" : methodId.replace("deb", "").toUpperCase();
        return ("debit_card".equals(typeId) ? "tarjeta de débito " : "tarjeta ") + card;
    }
}
