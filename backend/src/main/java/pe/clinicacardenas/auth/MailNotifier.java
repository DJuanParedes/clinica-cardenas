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
        if (!isConfigured()) {
            log.info("[correo simulado] Para: {} | Asunto: {} | HTML {} caracteres{}", to, subject, html.length(),
                    attachmentName == null ? "" : " | adjunto " + attachmentName);
            return false;
        }
        try {
            JavaMailSender s = sender.getObject();
            jakarta.mail.internet.MimeMessage msg = s.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper h = new org.springframework.mail.javamail.MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim(), html);
            if (attachmentName != null && attachment != null) {
                h.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment), attachmentType);
            }
            s.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo a {}: {}", to, e.getMessage());
            throw new pe.clinicacardenas.common.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "CORREO_NO_ENVIADO",
                    "No se pudo enviar el correo: " + e.getMessage());
        }
    }
}
