package pe.clinicacardenas.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class Dates {
    public static final ZoneId ZONE = ZoneId.of("America/Lima");

    private Dates() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Convierte el valor de una columna DATETIME/DATE leída con JDBC (según la versión del driver) a LocalDateTime. */
    public static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime l) return l;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (v instanceof java.time.LocalDate d) return d.atStartOfDay();
        if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }

    public static LocalDate toLocalDate(Object v) {
        LocalDateTime t = toLocalDateTime(v);
        return t == null ? null : t.toLocalDate();
    }
}
