package pe.clinicacardenas.admin;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestoreConversionTest {

    @Test
    void fechasEnSusDistintosFormatos() {
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 11, 28, 9, 12)), DeletionService.toDb("2026-11-28T09:12:00", "datetime"));
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 11, 28, 9, 12, 5)), DeletionService.toDb(List.of(2026, 11, 28, 9, 12, 5), "datetime"));
        assertEquals(LocalDate.of(2026, 12, 1), DeletionService.toDb("2026-12-01", "date"));
        assertEquals(LocalTime.of(8, 30), DeletionService.toDb("08:30:00", "time"));
        assertNull(DeletionService.toDb(null, "datetime"));
    }

    @Test
    void textoYJson() {
        assertEquals("hola", DeletionService.toDb("hola", "varchar"));
        assertEquals("{\"a\":1}", DeletionService.toDb(Map.of("a", 1), "json"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) DeletionService.toDb("AQID", "blob"));
    }
}
