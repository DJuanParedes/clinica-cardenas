package pe.clinicacardenas.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envía correos si hay un servidor SMTP configurado (MAIL_HOST).
 * Sin SMTP, deja el mensaje en el log del backend. El microservicio de
 * notificaciones (WhatsApp/correo) llega en un sprint posterior (HU-23, SP-02).
 */
@Component
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;

    public MailNotifier(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.host = host;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && sender.getIfAvailable() != null;
    }

    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.info("[correo simulado] Para: {} | Asunto: {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.getObject().send(msg);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo a {}: {}", to, e.getMessage());
        }
    }

    /** Correo HTML con un adjunto opcional (p. ej. el XML del comprobante). Devuelve false si quedó simulado o falló. */
    public boolean sendHtml(String to, String subject, String html, String attachmentName, byte[] attachment, String attachmentType) {
        java.util.Map<String, byte[]> m = new java.util.LinkedHashMap<>();
        if (attachmentName != null && attachment != null) m.put(attachmentName, attachment);
        return sendHtml(to, subject, html, m);
    }

    /** Correo HTML con varios adjuntos (nombre → contenido; el tipo se deduce de la extensión). */
    public boolean sendHtml(String to, String subject, String html, java.util.Map<String, byte[]> attachments) {
        if (!isConfigured()) {
            log.info("[correo simulado] Para: {} | Asunto: {} | HTML {} caracteres | adjuntos {}", to, subject, html.length(), attachments.keySet());
            return false;
        }
        try {
            JavaMailSender s = sender.getObject();
            jakarta.mail.internet.MimeMessage msg = s.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper h = new org.springframework.mail.javamail.MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim(), html);
            for (java.util.Map.Entry<String, byte[]> a : attachments.entrySet()) {
                String type = "application/octet-stream";
                if (a.getKey().endsWith(".pdf")) type = "application/pdf";
                else if (a.getKey().endsWith(".xml")) type = "application/xml";
                h.addAttachment(a.getKey(), new org.springframework.core.io.ByteArrayResource(a.getValue()), type);
            }
            s.send(msg);
            return true;
        } catch (Exception e) {
            throw new pe.clinicacardenas.common.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "CORREO_NO_ENVIADO",
                    "No se pudo enviar el correo: " + e.getMessage());
        }
    }
}
