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
}
