package pe.clinicacardenas.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.auth.MailNotifier;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.patient.PatientRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Envía al cliente la representación impresa del comprobante por correo, con el XML firmado adjunto
 * (RS 097-2012/SUNAT: el emisor electrónico debe poner el comprobante a disposición del adquiriente).
 * En la pantalla no se muestra el XML: es un archivo técnico, el cliente recibe el documento legible.
 */
@Service
public class InvoiceMailer {
    private static final String TD_TD_STYLE_PADDING_6PX_TEXT_ALIGN_RIGHT = "</td><td style='padding:6px;text-align:right;border-bottom:1px solid #eee'>";
    private static final String AMOUNTINWORDS = "amountInWords";
    private static final String COMPANY = "company";
    private static final String ITEMS = "items";


    private static final Pattern EMAIL = pe.clinicacardenas.common.Emails.PATTERN;

    private final BillingService billing;
    private final MailNotifier mail;
    private final PatientRepository patients;
    private final AuditService audit;

    @org.springframework.beans.factory.annotation.Value("${app.notifications.clinic-phone:987 654 000}")
    private String phone;

    /** PDF con formato SUNAT para descargar o imprimir desde la caja. */
    public byte[] pdf(Long id) {
        Invoice inv = billing.get(id);
        @SuppressWarnings("unchecked")
        Map<String, Object> d = billing.detail(id);
        return InvoicePdf.render(inv, (List<InvoiceItem>) d.get(ITEMS), (UblBuilder.Company) d.get(COMPANY), (String) d.get(AMOUNTINWORDS), phone);
    }

    public InvoiceMailer(BillingService billing, MailNotifier mail, PatientRepository patients, AuditService audit) {
        this.billing = billing;
        this.mail = mail;
        this.patients = patients;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> send(Long id, String to) {
        Invoice inv = billing.get(id);
        String email = to == null || to.isBlank()
                ? (inv.patientId == null ? null : patients.findById(inv.patientId).map(p -> p.email).orElse(null))
                : to.trim();
        if (email == null || !EMAIL.matcher(email).matches()) throw ApiException.field("email", "Escribe un correo válido para enviar el comprobante.");
        if ("BORRADOR".equals(inv.status) || "RECHAZADO".equals(inv.status)) {
            throw ApiException.badRequest("NO_ENVIABLE", "Solo se envían comprobantes emitidos (no borradores ni rechazados).");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> d = billing.detail(id);
        String html = html(inv, (List<InvoiceItem>) d.get(ITEMS), (UblBuilder.Company) d.get(COMPANY), (String) d.get(AMOUNTINWORDS));
        byte[] xml = inv.xmlContent == null ? null : inv.xmlContent.getBytes(StandardCharsets.UTF_8);
        String fileBase = ((UblBuilder.Company) d.get(COMPANY)).ruc() + "-" + inv.docType + "-" + inv.getFullNumber();
        byte[] pdf = InvoicePdf.render(inv, (List<InvoiceItem>) d.get(ITEMS), (UblBuilder.Company) d.get(COMPANY), (String) d.get(AMOUNTINWORDS), phone);
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        files.put(fileBase + ".pdf", pdf);
        if (xml != null) files.put(fileBase + ".xml", xml);
        boolean real = mail.sendHtml(email, inv.getDocTypeLabel() + " " + inv.getFullNumber() + " · Clínica Dental Cárdenas", html, files);
        audit.register("COMPROBANTE", inv.id, "ENVIAR_CORREO", inv.getFullNumber() + " → " + email);
        return Map.of("message", real ? "Comprobante enviado a " + email + "." : "Correo registrado (el servidor de correo no está configurado: quedó simulado).",
                "email", email, "simulated", !real);
    }

    private static String esc(Object o) {
        return o == null ? "" : String.valueOf(o).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : "S/ " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    static String html(Invoice inv, List<InvoiceItem> items, UblBuilder.Company c, String words) {
        StringBuilder rows = new StringBuilder();
        for (InvoiceItem it : items) {
            rows.append("<tr><td style='padding:6px;border-bottom:1px solid #eee'>").append(esc(it.description))
                    .append(TD_TD_STYLE_PADDING_6PX_TEXT_ALIGN_RIGHT).append(it.quantity.stripTrailingZeros().toPlainString())
                    .append(TD_TD_STYLE_PADDING_6PX_TEXT_ALIGN_RIGHT).append(money(it.unitPrice))
                    .append(TD_TD_STYLE_PADDING_6PX_TEXT_ALIGN_RIGHT).append(money(it.lineTotal)).append("</td></tr>");
        }
        return "<div style='font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1d2b33'>"
                + "<p>Estimado(a) " + esc(inv.customerName) + ":</p><p>Le enviamos su comprobante electrónico. El archivo XML adjunto es el documento con validez tributaria.</p>"
                + "<div style='border:1px solid #d6e2e6;border-radius:10px;padding:16px'>"
                + "<table width='100%'><tr><td><strong>" + esc(c.businessName()) + "</strong><br>" + esc(c.address()) + "</td>"
                + "<td style='text-align:right;border:1px solid #1d2b33;padding:8px'><strong>RUC " + esc(c.ruc()) + "</strong><br>"
                + esc(inv.getDocTypeLabel()).toUpperCase() + " ELECTRÓNICA<br><strong>" + esc(inv.getFullNumber()) + "</strong></td></tr></table>"
                + "<p>Fecha de emisión: " + inv.issueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "<br>Cliente: " + esc(inv.customerName)
                + "<br>Documento: " + esc(inv.customerDocNumber) + (inv.customerAddress == null ? "" : "<br>Dirección: " + esc(inv.customerAddress)) + "</p>"
                + "<table width='100%' style='border-collapse:collapse;font-size:14px'><tr style='background:#eef5f6'><th style='padding:6px;text-align:left'>Descripción</th>"
                + "<th style='padding:6px;text-align:right'>Cant.</th><th style='padding:6px;text-align:right'>P. unit.</th><th style='padding:6px;text-align:right'>Importe</th></tr>"
                + rows + "</table>"
                + "<p style='text-align:right'>Op. gravada: " + money(inv.taxableAmount) + "<br>IGV 18%: " + money(inv.igvAmount)
                + "<br><strong>Total: " + money(inv.totalAmount) + "</strong></p><p>SON: " + esc(words) + "</p>"
                + (inv.hash == null ? "" : "<p style='font-size:12px;color:#5b6b73'>Resumen (hash): " + esc(inv.hash) + "</p>")
                + "<p style='font-size:12px;color:#5b6b73'>Representación impresa del comprobante electrónico. Puede consultarlo en www.sunat.gob.pe</p></div>"
                + "<p>Gracias por su preferencia.<br>Clínica Dental Cárdenas</p></div>";
    }
}
