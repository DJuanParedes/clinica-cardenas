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
 * Consulta por DNI para autocompletar el registro con datos de RENIEC, a través de un proveedor autorizado
 * (RENIEC no publica una API abierta). Funciona con cualquier proveedor que reciba el DNI en la URL y un token
 * Bearer: Decolecta (por defecto), Factiliza, apiperu.dev, etc. Siempre devuelve nombres y apellidos; si el
 * plan del proveedor también entrega fecha de nacimiento, sexo, dirección o UBIGEO, se devuelven para
 * completar esos campos. El personal revisa antes de guardar.
 * Configuración: RENIEC_API_TOKEN y, opcionalmente, RENIEC_API_URL con {dni}. Respuestas en caché 24 h.
 */
@Service
public class DniLookupService {
    private static final String CONSULTA_DNI = "CONSULTA_DNI";


    private static final Logger log = LoggerFactory.getLogger(DniLookupService.class);

    private final String url;
    private final String token;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cachedAt = new ConcurrentHashMap<>();
    private static final long TTL_MS = 24L * 3600 * 1000;

    public DniLookupService(@Value("${app.reniec.url:https://api.decolecta.com/v1/reniec/dni?numero={dni}}") String url,
                            @Value("${app.reniec.token:}") String token, ObjectMapper json) {
        this.url = url == null || url.isBlank() ? "https://api.decolecta.com/v1/reniec/dni?numero={dni}" : url.trim();
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
        if (hit != null && System.currentTimeMillis() - cachedAt.getOrDefault(dni, 0L) < TTL_MS) return hit;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.replace("{dni}", dni)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404 || res.statusCode() == 422 || res.statusCode() == 400) {
                throw new ApiException(HttpStatus.NOT_FOUND, "DNI_NO_ENCONTRADO", "RENIEC no tiene registrado el DNI " + dni + ". Revisa el número.");
            }
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                log.warn("Consulta DNI: token rechazado ({})", res.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, CONSULTA_DNI, "El token de consulta RENIEC fue rechazado o venció. Escribe los datos a mano y avisa al administrador.");
            }
            if (res.statusCode() == 429) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, CONSULTA_DNI, "Se alcanzó el límite de consultas RENIEC del plan. Escribe los datos a mano.");
            }
            if (res.statusCode() / 100 != 2) {
                log.warn("Consulta DNI respondió {}", res.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, CONSULTA_DNI, "El servicio de consulta de DNI no respondió. Escribe los nombres a mano.");
            }
            JsonNode root = json.readTree(res.body());
            // Algunos proveedores envuelven la respuesta: { success, data: {...} }
            JsonNode n = root.path("data").isObject() ? root.path("data") : root;
            Map<String, Object> m = parse(dni, n);
            if (m == null) throw new ApiException(HttpStatus.NOT_FOUND, "DNI_NO_ENCONTRADO", "RENIEC no tiene registrado el DNI " + dni + ". Revisa el número.");
            cache.put(dni, m);
            cachedAt.put(dni, System.currentTimeMillis());
            return m;
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, CONSULTA_DNI, "El servicio de consulta de DNI no respondió.");
        } catch (Exception e) {
            log.warn("Consulta DNI falló: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, CONSULTA_DNI, "El servicio de consulta de DNI no respondió. Escribe los nombres a mano.");
        }
    }

    /** Lee la respuesta de cualquiera de los proveedores habituales (los nombres de los campos varían). */
    static Map<String, Object> parse(String dni, JsonNode n) {
        String first = text(n, "first_name", "nombres", "nombre", "prenombres");
        String last1 = text(n, "first_last_name", "apellidoPaterno", "apellido_paterno", "paterno");
        String last2 = text(n, "second_last_name", "apellidoMaterno", "apellido_materno", "materno");
        if (first.isBlank()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNumber", dni);
        m.put("firstName", title(first));
        m.put("lastName", title((last1 + " " + last2).trim()));
        String birth = isoDate(text(n, "fecha_nacimiento", "fechaNacimiento", "birth_date", "birthDate"));
        if (birth != null) m.put("birthDate", birth);
        String sex = text(n, "sexo", "genero", "gender", "sex").toUpperCase();
        if (sex.startsWith("M") || sex.equals("1")) m.put("sex", "M");
        else if (sex.startsWith("F") || sex.equals("2")) m.put("sex", "F");
        String address = text(n, "direccion", "direccion_completa", "domicilio", "address");
        if (!address.isBlank() && !address.matches("^[-\s]*$")) m.put("address", title(address));
        String ubigeo = text(n, "ubigeo_reniec", "ubigeo", "ubigeo_sunat");
        if (ubigeo.matches("^\\d{6}$")) m.put("ubigeo", ubigeo);
        String district = text(n, "distrito", "district");
        if (!district.isBlank()) m.put("district", title(district));
        m.put("source", "RENIEC");
        return m;
    }

    /** dd/mm/aaaa, dd-mm-aaaa o aaaa-mm-dd → aaaa-mm-dd. */
    static String isoDate(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        java.util.regex.Matcher a = java.util.regex.Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2}).*").matcher(s);
        if (a.matches()) return a.group(1) + "-" + a.group(2) + "-" + a.group(3);
        java.util.regex.Matcher b = java.util.regex.Pattern.compile("^(\\d{2})[/-](\\d{2})[/-](\\d{4})$").matcher(s);
        if (b.matches()) return b.group(3) + "-" + b.group(2) + "-" + b.group(1);
        return null;
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
