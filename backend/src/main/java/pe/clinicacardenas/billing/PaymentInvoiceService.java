package pe.clinicacardenas.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emite la boleta electrónica de un cobro de caja o de un pago en línea, sin pasar por la pantalla de comprobantes.
 * - Concepto: el procedimiento pagado (con su pieza) o "Pago a cuenta de tratamiento dental".
 * - Si el cobro cancela el saldo del procedimiento y este aún no está facturado, la línea queda vinculada a él.
 * - Idempotente: si el cobro ya tiene comprobante, lo devuelve sin emitir otro.
 * - El envío a SUNAT se intenta al final; si falla, el comprobante queda GENERADO y se reenvía desde Comprobantes.
 */
@Service
public class PaymentInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentInvoiceService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final BillingService billing;
    private final PatientRepository patients;

    public PaymentInvoiceService(JdbcTemplate jdbc, TransactionTemplate tx, BillingService billing, PatientRepository patients) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.billing = billing;
        this.patients = patients;
    }

    public Map<String, Object> issueForPayment(long paymentId) {
        Long invoiceId = tx.execute(s -> create(paymentId));
        if (invoiceId == null) throw ApiException.notFound("El cobro");
        Invoice inv = billing.get(invoiceId);
        if ("GENERADO".equals(inv.status)) {
            try {
                billing.send(invoiceId);
                inv = billing.get(invoiceId);
            } catch (Exception e) {
                log.warn("Boleta {} del cobro {} no enviada a SUNAT: {}", invoiceId, paymentId, e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoiceId", inv.id);
        out.put("invoice", inv.getFullNumber());
        out.put("status", inv.status);
        out.put("message", "Comprobante " + inv.getFullNumber() + ("ACEPTADO".equals(inv.status) ? " emitido y aceptado por SUNAT." : " emitido."));
        return out;
    }

    private Long create(long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, patient_id, treatment_item_id, amount, status, invoice_id from payments where id = ? for update", paymentId);
        if (rows.isEmpty()) return null;
        Map<String, Object> p = rows.get(0);
        if (p.get("invoice_id") != null) return ((Number) p.get("invoice_id")).longValue();
        if (!"VALIDO".equals(p.get("status"))) throw ApiException.conflict("COBRO_ANULADO", "El cobro está anulado: no se emite comprobante.");
        long patientId = ((Number) p.get("patient_id")).longValue();
        Patient patient = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        BigDecimal amount = (BigDecimal) p.get("amount");

        String concept = "Pago a cuenta de tratamiento dental";
        Long linkItem = null;
        if (p.get("treatment_item_id") != null) {
            long itemId = ((Number) p.get("treatment_item_id")).longValue();
            List<Map<String, Object>> it = jdbc.queryForList("""
                    select pr.name, ti.tooth, ti.price, ti.invoice_id,
                           ti.price - coalesce((select sum(py.amount) from payments py
                                                 where py.treatment_item_id = ti.id and py.status = 'VALIDO'), 0) as pending,
                           (select count(*) from payments py where py.treatment_item_id = ti.id and py.status = 'VALIDO') as cobros
                      from treatment_items ti join procedures pr on pr.id = ti.procedure_id where ti.id = ?
                    """, itemId);
            if (!it.isEmpty()) {
                Map<String, Object> i = it.get(0);
                String name = (String) i.get("name") + (i.get("tooth") == null ? "" : " (pieza " + i.get("tooth") + ")");
                boolean single = ((Number) i.get("cobros")).intValue() == 1;
                boolean paidOff = ((BigDecimal) i.get("pending")).signum() <= 0;
                if (single && paidOff && i.get("invoice_id") == null) {
                    concept = name;
                    linkItem = itemId;
                } else {
                    concept = "Pago a cuenta: " + name;
                }
            }
        }
        String customerDocType = switch (patient.docType == null ? "" : patient.docType) {
            case "DNI" -> "1";
            case "CE" -> "4";
            case "PAS" -> "7";
            default -> "0";
        };
        Map<String, Object> created = billing.create(new BillingService.InvoiceRequest("03", patient.id, customerDocType,
                patient.docNumber, patient.getFullName(), null,
                List.of(new BillingService.LineRequest(concept, BigDecimal.ONE, amount, linkItem))));
        Invoice inv = (Invoice) created.get("invoice");
        jdbc.update("update payments set invoice_id = ? where id = ?", inv.id, paymentId);
        return inv.id;
    }
}
