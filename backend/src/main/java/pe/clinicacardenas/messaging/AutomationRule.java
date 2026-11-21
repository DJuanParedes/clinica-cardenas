package pe.clinicacardenas.messaging;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Regla de automatización: cuándo se envía cada aviso respecto de la cita.
 * ANTES: amount horas antes del inicio, o amount días antes a la hora send_time.
 * DIA: el mismo día de la cita a send_time (si la cita es muy temprano, 2 horas antes).
 * DESPUES: al quedar atendida, amount horas después del fin, o amount días después a send_time.
 */
public record AutomationRule(String code, String name, String kind, int amount, String unit, LocalTime sendTime,
                             String templateCode, String notifType, boolean active) {

    /** Momento de envío para una cita, o null si ya no tiene sentido enviarlo. */
    public LocalDateTime when(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        LocalDateTime at;
        switch (kind) {
            case "ANTES" -> {
                if ("DIAS".equals(unit)) {
                    at = start.toLocalDate().minusDays(amount).atTime(sendTime == null ? LocalTime.of(10, 0) : sendTime);
                    if (!at.isBefore(start.minusHours(1))) return null;
                } else {
                    at = start.minusHours(amount);
                    // recordatorio con confirmación: si la cita es antes de ese plazo, 2 h antes (HU-23)
                    if (!at.isAfter(now.plusMinutes(5)) && "AUTO_1D".equals(code)) at = start.minusHours(2);
                }
            }
            case "DIA" -> {
                at = start.toLocalDate().atTime(sendTime == null ? LocalTime.of(7, 30) : sendTime);
                if (!at.isBefore(start.minusHours(1))) at = start.minusHours(2);
                if (at.toLocalDate().isBefore(start.toLocalDate())) return null;
            }
            case "DESPUES" -> {
                LocalDateTime base = end == null ? now : end;
                at = "DIAS".equals(unit)
                        ? base.toLocalDate().plusDays(amount).atTime(sendTime == null ? LocalTime.of(18, 0) : sendTime)
                        : base.plusHours(amount);
                if (at.isBefore(now)) at = now; // se marcó atendida tarde: se envía ya
                return at;
            }
            default -> { return null; }
        }
        return at.isAfter(now.plusMinutes(5)) ? at : null;
    }
}
