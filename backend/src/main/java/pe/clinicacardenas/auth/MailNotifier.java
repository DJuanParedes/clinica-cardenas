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
}
