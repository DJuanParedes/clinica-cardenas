package pe.clinicacardenas.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Enlace firmado para calificar una cita sin iniciar sesión: /encuesta/{token}. El token lleva el id de la cita y
 * una firma HMAC con el secreto del servidor, así nadie puede calificar citas ajenas cambiando el número.
 */
@Component
public class SurveyLinks {

    private final byte[] key;
    private final String publicUrl;

    public SurveyLinks(@Value("${app.jwt.secret}") String secret, @Value("${app.public-url:}") String publicUrl) {
        this.key = ("encuesta:" + secret).getBytes(StandardCharsets.UTF_8);
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    public String token(long appointmentId) {
        String id = Long.toString(appointmentId, 36);
        return id + "." + sign(id);
    }

    /** Id de la cita si la firma es válida; si no, null. */
    public Long parse(String token) {
        if (token == null || !token.matches("^[0-9a-z]{1,13}\\.[A-Za-z0-9_-]{16}$")) return null;
        String[] p = token.split("\\.");
        if (!MessageDigest.isEqual(sign(p[0]).getBytes(StandardCharsets.UTF_8), p[1].getBytes(StandardCharsets.UTF_8))) return null;
        return Long.parseLong(p[0], 36);
    }

    public String surveyUrl(long appointmentId) {
        return base() + "/encuesta/" + token(appointmentId);
    }

    public String bookingUrl() {
        return base() + "/reservar";
    }

    public String base() {
        return publicUrl.isBlank() ? "http://localhost:9081" : publicUrl;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] h = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
