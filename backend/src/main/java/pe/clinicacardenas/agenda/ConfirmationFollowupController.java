package pe.clinicacardenas.agenda;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.portal.PortalService;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Citas que el paciente aún no confirma. El paciente confirma hasta 2 horas antes (app, portal o respondiendo "1");
 * pasado ese plazo la cita aparece en rojo para que recepción lo llame y registre el resultado de la llamada.
 * Se muestran las de las próximas 24 horas, primero las vencidas.
 */
@RestController
public class ConfirmationFollowupController {

    private final JdbcTemplate jdbc;
    private final AgendaService agenda;
    private final AuditService audit;

    public ConfirmationFollowupController(JdbcTemplate jdbc, AgendaService agenda, AuditService audit) {
        this.jdbc = jdbc;
        this.agenda = agenda;
        this.audit = audit;
    }

    @GetMapping("/api/agenda/unconfirmed")
    @PreAuthorize("hasAnyAuthority('CITAS_GESTIONAR','CITAS_ESTADO')")
    public Map<String, Object> unconfirmed() {
        LocalDateTime now = Dates.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select a.id, a.start_at as startAt, a.reason, a.call_note as callNote, a.call_at as callAt, a.call_by as callBy,
                       p.id as patientId, concat(p.first_name, ' ', p.last_name) as patient, p.phone, u.full_name as dentist
                  from appointments a join patients p on p.id = a.patient_id join dentists d on d.id = a.dentist_id join users u on u.id = d.user_id
                 where a.status = 'PROGRAMADA' and a.start_at > ? and a.start_at <= ?
                 order by a.start_at""", now, now.plusHours(24));
        long overdue = 0;
        for (Map<String, Object> r : rows) {
            LocalDateTime start = Dates.toLocalDateTime(r.get("startAt"));
            boolean late = !start.isAfter(now.plusHours(PortalService.CONFIRM_HOURS));
            r.put("deadline", start.minusHours(PortalService.CONFIRM_HOURS));
            r.put("overdue", late);
            if (late) overdue++;
        }
        rows.sort((x, y) -> Boolean.compare((Boolean) y.get("overdue"), (Boolean) x.get("overdue")));
        return Map.of("items", rows, "overdue", overdue, "total", rows.size());
    }

    public record CallResult(String result, String note) {}

    /** Resultado de la llamada de recepción: confirmó, no contesta (queda la nota) o pide cambiar. */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @PostMapping("/api/appointments/{id}/call")
    @PreAuthorize("hasAnyAuthority('CITAS_GESTIONAR','CITAS_ESTADO')")
    @Transactional
    public Map<String, Object> call(@PathVariable Long id, @RequestBody CallResult r) {
        String result = r.result() == null ? "" : r.result().trim().toUpperCase();
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        String label = switch (result) {
            case "CONFIRMADA" -> "Confirmó por teléfono";
            case "NO_CONTESTA" -> "No contesta";
            case "REPROGRAMAR" -> "Pide reprogramar";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Elige el resultado de la llamada.");
        };
        String text = label + (note == null ? "" : " · " + note);
        if (text.length() > 200) text = text.substring(0, 200);
        int n = jdbc.update("update appointments set call_note = ?, call_at = ?, call_by = ? where id = ? and status = 'PROGRAMADA'",
                text, Dates.now(), CurrentUser.usernameOrSystem(), id);
        if (n == 0) throw ApiException.conflict("NO_PENDIENTE", "La cita ya no está pendiente de confirmación.");
        if ("CONFIRMADA".equals(result)) {
            agenda.changeStatus(id, new AgendaService.StatusRequest(AppointmentStatus.CONFIRMADA, "Confirmada por teléfono (recepción)" + (note == null ? "" : ": " + note)));
        } else if ("REPROGRAMAR".equals(result)) {
            jdbc.update("update appointments set reschedule_requested_at = ? where id = ?", Dates.now(), id);
        }
        audit.record("CITA", id, "LLAMADA", text);
        // changeStatus guarda con JPA: se vuelca a la base antes de releer la lista con SQL en esta misma transacción.
        em.flush();
        return unconfirmed();
    }
}
