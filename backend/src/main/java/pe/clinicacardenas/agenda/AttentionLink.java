package pe.clinicacardenas.agenda;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.clinicacardenas.common.Dates;

import java.util.List;
import java.util.Map;

/**
 * v2.17: amarra lo que pasa en la atención con la cita del día. Busca la cita de hoy del paciente que está
 * en atención (o ya atendida, o en espera) para registrar en ella los procedimientos, evoluciones, cobros y recetas.
 */
@Component
public class AttentionLink {
    private final JdbcTemplate jdbc;

    public AttentionLink(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long current(Long patientId) { return current(jdbc, patientId); }

    public static Long current(JdbcTemplate jdbc, Long patientId) {
        if (patientId == null) return null;
        var day = Dates.today();
        List<Long> ids = jdbc.queryForList("""
                select id from appointments
                 where patient_id = ? and start_at >= ? and start_at < ?
                   and status in ('EN_ATENCION','ATENDIDA','EN_ESPERA')
                 order by case status when 'EN_ATENCION' then 0 when 'ATENDIDA' then 1 else 2 end, start_at desc limit 1""",
                Long.class, patientId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Resumen de la atención: procedimientos realizados, evoluciones, cobros y recetas de la cita. */
    public Map<String, Object> summary(Long appointmentId) {
        return Map.of(
            "procedures", jdbc.queryForList("""
                select ti.id, pr.name as `procedure`, ti.tooth, ti.price from treatment_items ti
                  join procedures pr on pr.id = ti.procedure_id where ti.done_appointment_id = ? order by ti.id""", appointmentId),
            "evolutions", jdbc.queryForList("""
                select e.id, e.notes, e.session_date as date, e.voided_at as voidedAt from evolutions e
                 where e.appointment_id = ? order by e.id""", appointmentId),
            "payments", jdbc.queryForList("""
                select p.id, p.amount, p.method, p.status, p.paid_at as paidAt from payments p
                 where p.appointment_id = ? order by p.id""", appointmentId),
            "prescriptions", jdbc.queryForList("""
                select r.id, r.created_at as createdAt, r.file_id as fileId, r.voided_at as voidedAt,
                       (select group_concat(i.drug separator ', ') from prescription_items i where i.prescription_id = r.id) as drugs
                  from prescriptions r where r.appointment_id = ? order by r.id""", appointmentId));
    }
}
