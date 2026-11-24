package pe.clinicacardenas.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.clinicacardenas.common.ApiException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente de la API REST de Mercado Pago (https://api.mercadopago.com), sin SDK.
 * - Checkout Pro: crea una "preferencia" y devuelve el link donde el paciente paga con tarjeta, Yape o su cuenta.
 * - Consulta pagos por id o por external_reference (así no depende solo del webhook).
 * - Valida la firma x-signature de los webhooks (HMAC-SHA256 con la clave secreta de la aplicación).
 * Con un Access Token de prueba (TEST-…) todo funciona en el entorno de pruebas de Mercado Pago.
 */
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final String API = "https://api.mercadopago.com";

    private final String accessToken;
    private final String webhookSecret;
    private final String testModeSetting;
    private volatile Boolean testUser;
    private volatile long testUserCheckedAt;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public MercadoPagoClient(@Value("${app.payments.mercadopago.access-token:}") String accessToken,
                             @Value("${app.payments.mercadopago.webhook-secret:}") String webhookSecret,
                             @Value("${app.payments.mercadopago.test-mode:auto}") String testModeSetting,
                             ObjectMapper json) {
        this.testModeSetting = testModeSetting == null ? "auto" : testModeSetting.trim().toLowerCase();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.json = json;
    }

    public boolean configured() {
        return !accessToken.isEmpty();
    }

    /**
     * Modo de pruebas. MP_MODO_PRUEBA=true|false lo fija; en "auto" se detecta: llaves TEST-… o credenciales de prueba
     * nuevas (APP_USR-… de un usuario de prueba, etiqueta test_user en /users/me).
     */
    public boolean testMode() {
        if ("true".equals(testModeSetting)) return true;
        if ("false".equals(testModeSetting)) return false;
        if (accessToken.startsWith("TEST-")) return true;
        if (!configured()) return false;
        Boolean known = testUser;
        if (known != null) return known;
        if (System.currentTimeMillis() - testUserCheckedAt < 600_000) return false;
        testUserCheckedAt = System.currentTimeMillis();
        try {
            JsonNode me = call("GET", "/users/me", null, null);
            boolean test = false;
            for (JsonNode t : me.path("tags")) if ("test_user".equals(t.asText())) test = true;
            if (me.path("nickname").asText("").toUpperCase().startsWith("TESTUSER")) test = true;
            testUser = test;
            return test;
        } catch (RuntimeException e) {
            log.warn("No se pudo saber si las credenciales de Mercado Pago son de prueba: {}", e.getMessage());
            return false;
        }
    }

    public JsonNode createPreference(Map<String, Object> body) {
        return call("POST", "/checkout/preferences", body, UUID.randomUUID().toString());
    }

    public JsonNode payment(String paymentId) {
        return call("GET", "/v1/payments/" + URLEncoder.encode(paymentId, StandardCharsets.UTF_8), null, null);
    }

    /** Pagos de una referencia nuestra, el más reciente primero. */
    public JsonNode searchByReference(String externalReference) {
        return call("GET", "/v1/payments/search?sort=date_created&criteria=desc&external_reference="
                + URLEncoder.encode(externalReference, StandardCharsets.UTF_8), null, null);
    }

    private JsonNode call(String method, String path, Object body, String idempotencyKey) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAGOS_NO_CONFIGURADOS",
                    "Los pagos con Mercado Pago no están configurados: falta MP_ACCESS_TOKEN en el servidor.");
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json");
            if (idempotencyKey != null) b.header("X-Idempotency-Key", idempotencyKey);
            if ("POST".equals(method)) b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            else b.GET();
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
            if (res.statusCode() / 100 != 2) {
                String msg = node.path("message").asText("HTTP " + res.statusCode());
                log.warn("Mercado Pago {} {} → {} {}", method, path, res.statusCode(), msg);
                if (res.statusCode() == 401) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "MP_CREDENCIALES", "Mercado Pago rechazó las credenciales (revisa MP_ACCESS_TOKEN).");
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MP_ERROR", "Mercado Pago respondió: " + msg);
            }
            return node;
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MP_NO_DISPONIBLE", "Mercado Pago no respondió. Intenta de nuevo.");
        } catch (Exception e) {
            log.warn("Mercado Pago {} {} falló: {}", method, path, e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MP_NO_DISPONIBLE", "Mercado Pago no respondió. Intenta de nuevo.");
        }
    }

    /**
     * Firma de los webhooks: x-signature = "ts=…,v1=…"; manifiesto "id:{data.id};request-id:{x-request-id};ts:{ts};"
     * firmado con HMAC-SHA256 y la clave secreta. Sin clave configurada se acepta (el pago igual se verifica
     * consultando a Mercado Pago, así que un aviso falso no registra nada).
     */
    public boolean validSignature(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret.isEmpty()) return true;
        if (xSignature == null || xSignature.isBlank()) return false;
        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("ts".equals(kv[0])) ts = kv[1];
            if ("v1".equals(kv[0])) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) manifest.append("id:").append(dataId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) manifest.append("request-id:").append(xRequestId).append(';');
        manifest.append("ts:").append(ts).append(';');
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
