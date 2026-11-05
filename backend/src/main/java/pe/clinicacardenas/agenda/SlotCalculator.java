package pe.clinicacardenas.agenda;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula los horarios libres de un odontólogo (HU-07).
 * Lógica pura, sin base de datos, para que sea fácil de probar.
 */
public final class SlotCalculator {

    private SlotCalculator() {}

    public record Block(LocalTime start, LocalTime end) {}
    public record Busy(LocalDateTime start, LocalDateTime end) {}
    public record Slot(LocalDateTime start, LocalDateTime end, boolean available, String reason) {}

    public static int stepFor(int durationMin) {
        return durationMin % 30 == 0 ? 30 : 15;
    }

    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    public static boolean fitsInBlocks(List<Block> blocks, LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) return false; // la cita no puede cruzar la medianoche
        for (Block b : blocks) {
            if (!start.isBefore(b.start()) && !end.isAfter(b.end())) return true;
        }
        return false;
    }

    public static List<Slot> compute(LocalDate date, List<Block> blocks, List<Busy> busy,
                                     int durationMin, LocalDateTime now) {
        List<Slot> out = new ArrayList<>();
        int step = stepFor(durationMin);
        for (Block b : blocks) {
            LocalDateTime cursor = date.atTime(b.start());
            LocalDateTime blockEnd = date.atTime(b.end());
            while (!cursor.plusMinutes(durationMin).isAfter(blockEnd)) {
                LocalDateTime end = cursor.plusMinutes(durationMin);
                String reason = null;
                if (cursor.isBefore(now)) {
                    reason = "PASADO";
                } else {
                    for (Busy x : busy) {
                        if (overlaps(cursor, end, x.start(), x.end())) {
                            reason = "OCUPADO";
                            break;
                        }
                    }
                }
                out.add(new Slot(cursor, end, reason == null, reason));
                cursor = cursor.plusMinutes(step);
            }
        }
        return out;
    }
}
