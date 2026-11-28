package pe.clinicacardenas.portal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.clinicacardenas.common.Dates;

/**
 * v2.19.3: una calificación baja (1 a 3 estrellas) no queda solo en la estadística: crea una tarea de alta prioridad
 * para que la clínica llame al paciente, como se le promete en el mensaje de agradecimiento.
 */
@Component
public class SurveyFollowUp {
    private final JdbcTemplate jdbc;

    public SurveyFollowUp(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void afterRating(Survey s) {
        if (s == null || s.score == null || s.score > 3) return;
        jdbc.update("""
                insert into tasks (patient_id, kind, title, notes, due_date, priority, status, origin, origin_ref, created_by, created_at)
                values (?, 'LLAMAR', ?, ?, ?, 'ALTA', 'PENDIENTE', 'ENCUESTA', ?, 'sistema', ?)""",
                s.patientId, "Llamar: calificó la atención con " + s.score + (s.score == 1 ? " estrella" : " estrellas"),
                s.comment == null ? "Sin comentario. Pregunta qué podemos mejorar." : "Comentario: " + s.comment,
                Dates.today(), String.valueOf(s.appointmentId), Dates.now());
    }
}
