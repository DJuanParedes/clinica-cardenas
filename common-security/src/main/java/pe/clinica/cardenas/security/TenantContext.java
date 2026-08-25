package pe.clinica.cardenas.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contexto de la clínica activa durante una petición.
 * El valor autenticado proviene exclusivamente de un JWT firmado.
 */
public final class TenantContext {
    public static final String DEFAULT_CLINIC = "CARDENAS";
    public static final String PUBLIC_HEADER = "X-Clinic-Id";
    private static final Pattern VALID = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,39}");
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static String currentClinicId() {
        String value = CURRENT.get();
        return value == null ? DEFAULT_CLINIC : value;
    }

    public static void setClinicId(String value) {
        CURRENT.set(normalize(value));
    }

    public static String normalize(String value) {
        String normalized = value == null ? DEFAULT_CLINIC : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) return DEFAULT_CLINIC;
        return normalized;
    }

    public static void clear() { CURRENT.remove(); }
}
