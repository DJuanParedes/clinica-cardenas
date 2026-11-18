package pe.clinicacardenas.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.common.ApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta de nombres por DNI para autocompletar el registro (datos de RENIEC a través de un proveedor como
 * Decolecta / apis.net.pe). Solo devuelve nombres y apellidos; el personal confirma antes de guardar.
 * Configuración: RENIEC_API_TOKEN (y opcionalmente RENIEC_API_URL con {dni}). Respuestas en caché 24 h.
 */
@Service
public class DniLookupService {

    private static final Logger log = LoggerFactory.getLogger(DniLookupService.class);

    private final String url;
    private final String token;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public DniLookupService(@Value("${app.reniec.url:https://api.decolecta.com/v1/reniec/dni?numero={dni}}") String url,
                            @Value("${app.reniec.token:}") String token, ObjectMapper json) {
        this.url = url;
        this.token = token == null ? "" : token.trim();
        this.json = json;
    }

    public boolean enabled() {
        return !token.isEmpty();
    }

    public Map<String, Object> lookup(String number) {
        String dni = number == null ? "" : number.trim();
        if (!dni.matches("^\\d{8}$")) throw ApiException.field("docNumber", "El DNI debe tener 8 dígitos.");
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONSULTA_NO_CONFIGURADA",
                    "La consulta de DNI no está configurada (falta RENIEC_API_TOKEN). Escribe los nombres a mano.");
        }
        Map<String, Object> hit = cache.get(dni);
        if (hit != null) return hit;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.replace("{dni}", dni)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404 || res.statusCode() == 422) {
                throw ApiException.notFound("El DNI " + dni);
            }
            if (res.statusCode() / 100 != 2) {
                log.warn("Consulta DNI respondió {}", res.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CONSULTA_DNI", "El servicio de consulta de DNI no respondió. Escribe los nombres a mano.");
            }
            JsonNode n = json.readTree(res.body());
            String first = text(n, "first_name", "nombres");
            String last1 = text(n, "first_last_name", "apellidoPaterno", "apellido_paterno");
            String last2 = text(n, "second_last_name", "apellidoMaterno", "apellido_materno");
            if (first.isBlank()) throw ApiException.notFound("El DNI " + dni);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("docNumber", dni);
            m.put("firstName", title(first));
            m.put("lastName", title((last1 + " " + last2).trim()));
            m.put("source", "RENIEC");
            cache.put(dni, m);
            return m;
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CONSULTA_DNI", "El servicio de consulta de DNI no respondió.");
        } catch (Exception e) {
            log.warn("Consulta DNI falló: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CONSULTA_DNI", "El servicio de consulta de DNI no respondió. Escribe los nombres a mano.");
        }
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
        }
        return "";
    }

    private static String title(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
