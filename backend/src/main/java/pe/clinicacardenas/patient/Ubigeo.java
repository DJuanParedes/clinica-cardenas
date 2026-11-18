package pe.clinicacardenas.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Catálogo UBIGEO del INEI (25 departamentos, 196 provincias, 1 893 distritos) cargado una vez desde el classpath. */
public final class Ubigeo {

    /** código de distrito → [departamento, provincia, distrito] */
    private static final Map<String, String[]> DISTRICTS = new HashMap<>();

    static {
        try (InputStream in = Ubigeo.class.getResourceAsStream("/data/ubigeo.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode dep : root) {
                for (JsonNode prov : dep.get(2)) {
                    for (JsonNode dist : prov.get(2)) {
                        DISTRICTS.put(dist.get(0).asText(), new String[]{dep.get(1).asText(), prov.get(1).asText(), dist.get(1).asText()});
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo UBIGEO", e);
        }
    }

    private Ubigeo() {}

    public static boolean exists(String code) {
        return code != null && DISTRICTS.containsKey(code.trim());
    }

    /** [departamento, provincia, distrito] o null. */
    public static String[] names(String code) {
        return code == null ? null : DISTRICTS.get(code.trim());
    }
}
