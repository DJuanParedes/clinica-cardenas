package pe.clinicacardenas.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Escrituras idempotentes (v2.12). La app móvil guarda sus escrituras sin conexión y las reintenta al sincronizar;
 * cada una lleva una clave única (Idempotency-Key). La primera vez se ejecuta y se guarda la respuesta; los reintentos
 * con la misma clave devuelven esa respuesta sin volver a ejecutar, así una reserva nunca se duplica.
 */
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> run(String key, String scope, String principal, Supplier<Map<String, Object>> action) {
        if (key == null || key.isBlank()) return action.get();
        String k = key.trim();
        if (k.length() > 80) throw ApiException.badRequest("CLAVE_INVALIDA", "La clave de idempotencia es demasiado larga.");
        try {
            jdbc.update("insert into idempotency_keys (idem_key, scope, principal, created_at) values (?, ?, ?, ?)", k, scope, principal, Dates.now());
        } catch (DuplicateKeyException e) {
            List<String> saved = jdbc.queryForList("select response from idempotency_keys where idem_key = ? and scope = ? and principal = ?", String.class, k, scope, principal);
            if (saved.isEmpty() || saved.get(0) == null) {
                throw ApiException.conflict("EN_CURSO", "Esta operación ya se está procesando. Vuelve a intentarlo en unos segundos.");
            }
            try {
                return json.readValue(saved.get(0), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        try {
            Map<String, Object> result = action.get();
            jdbc.update("update idempotency_keys set status = 200, response = ? where idem_key = ? and scope = ? and principal = ?",
                    json.writeValueAsString(result), k, scope, principal);
            return result;
        } catch (RuntimeException e) {
            // Si falló (por ejemplo el horario ya no está libre) la clave se libera: el error se informa y no se guarda.
            jdbc.update("delete from idempotency_keys where idem_key = ? and scope = ? and principal = ?", k, scope, principal);
            throw e;
        } catch (Exception e) {
            jdbc.update("delete from idempotency_keys where idem_key = ? and scope = ? and principal = ?", k, scope, principal);
            throw new IllegalStateException(e);
        }
    }

    /** Las claves solo hacen falta mientras la app puede reintentar: se guardan 7 días. */
    @Scheduled(cron = "0 15 3 * * *", zone = "America/Lima")
    public void purge() {
        jdbc.update("delete from idempotency_keys where created_at < ?", Dates.now().minusDays(7));
    }
}
