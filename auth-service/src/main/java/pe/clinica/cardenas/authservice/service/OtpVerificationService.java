package pe.clinica.cardenas.authservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.authservice.domain.RecoveryChannel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpVerificationService {
    private static final String APPROVED_STATUS = "\"status\":\"approved\"";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, EmailChallenge> emailChallenges = new ConcurrentHashMap<>();
    private final JavaMailSender mailSender;

    @Value("${security.otp.provider:local}")
    private String provider;
    @Value("${security.otp.local-code:}")
    private String localCode;
    @Value("${security.otp.twilio.service-sid:}")
    private String serviceSid;
    @Value("${security.otp.twilio.api-key:}")
    private String apiKey;
    @Value("${security.otp.twilio.api-secret:}")
    private String apiSecret;
    @Value("${security.otp.email.from:}")
    private String emailFrom;
    @Value("${security.otp.email.ttl-minutes:10}")
    private int emailTtlMinutes;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${spring.mail.password:}")
    private String mailPassword;

    @PostConstruct
    void validateConfiguration() {
        String selected = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if ("local".equals(selected) && (blank(localCode) || !isSixDigitCode(localCode))) {
            throw new IllegalStateException("OTP_PROVIDER=local requiere LOCAL_OTP_CODE de 6 dígitos; ejecuta PREPARAR-ENTORNO-LOCAL.bat");
        }
        if ("twilio".equals(selected)) {
            if (blank(serviceSid) || blank(apiKey) || blank(apiSecret)) {
                throw new IllegalStateException("OTP_PROVIDER=twilio requiere TWILIO_VERIFY_SERVICE_SID, TWILIO_API_KEY y TWILIO_API_SECRET");
            }
            if (!serviceSid.startsWith("VA")) {
                throw new IllegalStateException("TWILIO_VERIFY_SERVICE_SID debe ser un SID de Verify que empiece por VA");
            }
        }
        if (isEmailProvider(selected)) {
            if (blank(mailUsername) || blank(mailPassword) || blank(emailFrom)) {
                throw new IllegalStateException("OTP_PROVIDER=email requiere MAIL_USERNAME, MAIL_PASSWORD y MAIL_FROM");
            }
            if (!validEmail(emailFrom.trim())) {
                throw new IllegalStateException("MAIL_FROM debe ser un correo válido");
            }
            if (emailTtlMinutes < 2 || emailTtlMinutes > 30) {
                throw new IllegalStateException("MAIL_OTP_TTL_MINUTES debe estar entre 2 y 30");
            }
        }
        if (!"local".equals(selected) && !"twilio".equals(selected) && !isEmailProvider(selected)) {
            throw new IllegalStateException("OTP_PROVIDER debe ser local, twilio o email");
        }
    }

    public void start(String destination, RecoveryChannel channel) {
        String selected = normalizedProvider();
        if ("local".equals(selected)) {
            log.warn("OTP_PROVIDER=local activo para {} por {}. Consulta el código únicamente con MOSTRAR-OTP-LOCAL.bat; no usar este proveedor en producción.",
                    mask(destination), channel);
            return;
        }
        if (isEmailProvider(selected)) {
            if (channel != RecoveryChannel.EMAIL) {
                throw unavailable("El proveedor de correo requiere el canal EMAIL");
            }
            sendEmailCode(destination);
            return;
        }
        ensureTwilio(channel);
        String twilioChannel = channel == RecoveryChannel.WHATSAPP ? "whatsapp" : "sms";
        post("https://verify.twilio.com/v2/Services/" + encPath(serviceSid) + "/Verifications",
                "To=" + enc(destination) + "&Channel=" + enc(twilioChannel));
    }

    public boolean check(String destination, RecoveryChannel channel, String code) {
        String selected = normalizedProvider();
        if ("local".equals(selected)) return localCode.equals(code);
        if (isEmailProvider(selected)) {
            if (channel != RecoveryChannel.EMAIL) return false;
            return checkEmailCode(destination, code);
        }
        ensureTwilio(channel);
        String body = post("https://verify.twilio.com/v2/Services/" + encPath(serviceSid) + "/VerificationCheck",
                "To=" + enc(destination) + "&Code=" + enc(code));
        return compactJson(body).contains(APPROVED_STATUS);
    }

    private void sendEmailCode(String destination) {
        String email = normalizeEmail(destination);
        if (!validEmail(email)) throw unavailable("Correo de recuperación no válido");
        cleanupExpiredChallenges();
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        String nonce = UUID.randomUUID().toString();
        EmailChallenge challenge = new EmailChallenge(hash(nonce, code), nonce, Instant.now().plus(Duration.ofMinutes(emailTtlMinutes)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom.trim());
        message.setTo(email);
        message.setSubject("Código para restablecer tu contraseña — Clínica Cárdenas");
        message.setText("""
                Hola:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta administrativa.

                Código de verificación: %s

                Este código vence en %d minutos. Si no solicitaste el cambio, ignora este mensaje y no compartas el código.

                Plataforma Multiclínica Odontológica
                Clínica Cárdenas
                """.formatted(code, emailTtlMinutes));
        try {
            mailSender.send(message);
            emailChallenges.put(email, challenge);
            log.info("Código de recuperación enviado por correo a {}", mask(email));
        } catch (MailException ex) {
            log.warn("No se pudo enviar el correo de recuperación: {}", ex.getClass().getSimpleName());
            throw unavailable("No se pudo enviar el código por correo");
        }
    }

    private boolean checkEmailCode(String destination, String code) {
        String email = normalizeEmail(destination);
        EmailChallenge challenge = emailChallenges.get(email);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            emailChallenges.remove(email);
            return false;
        }
        String receivedHash = hash(challenge.nonce(), code == null ? "" : code.trim());
        boolean approved = MessageDigest.isEqual(
                challenge.hash().getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8));
        if (approved) emailChallenges.remove(email, challenge);
        return approved;
    }

    private void cleanupExpiredChallenges() {
        Instant now = Instant.now();
        emailChallenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String post(String url, String form) {
        try {
            String basic = Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Proveedor OTP respondió HTTP {}", response.statusCode());
                throw unavailable("No se pudo enviar o validar el código de verificación");
            }
            return response.body() == null ? "" : response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Servicio de verificación temporalmente no disponible");
        } catch (IOException e) {
            throw unavailable("Servicio de verificación temporalmente no disponible");
        }
    }

    private void ensureTwilio(RecoveryChannel channel) {
        if (!"twilio".equals(normalizedProvider())) throw unavailable("Proveedor OTP no configurado");
        if (channel == RecoveryChannel.EMAIL) throw unavailable("Twilio Verify no admite el canal EMAIL en esta configuración");
    }

    private static String hash(String nonce, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((nonce + ":" + code).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo proteger el código de recuperación", ex);
        }
    }

    private String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailProvider(String value) {
        return "email".equals(value) || "smtp".equals(value);
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static boolean isSixDigitCode(String value) {
        return value != null && value.length() == 6 && value.chars().allMatch(Character::isDigit);
    }

    private static boolean validEmail(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) return false;
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < value.length() - 1;
    }

    private static String compactJson(String value) {
        return value == null ? "" : value.replace(" ", "").replace("\t", "").replace("\r", "").replace("\n", "");
    }
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encPath(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    private static String mask(String destination) {
        if (destination == null || destination.isBlank()) return "***";
        int at = destination.indexOf('@');
        if (at > 1) return destination.charAt(0) + "***" + destination.substring(at);
        return destination.length() < 5 ? "***" : "***" + destination.substring(destination.length() - 4);
    }

    private record EmailChallenge(String hash, String nonce, Instant expiresAt) {}
}
