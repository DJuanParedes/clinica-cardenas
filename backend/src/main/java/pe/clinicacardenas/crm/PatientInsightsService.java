package pe.clinicacardenas.crm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.common.Dates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * v2.7: datos de seguimiento por paciente para la lista: última y próxima cita, tarea pendiente más cercana,
 * avance del presupuesto (planes activos) y saldo por pagar. Se calcula en lote para la página que se muestra.
 */
@Service
public class PatientInsightsService {

    private final NamedParameterJdbcTemplate jdbc;

    public PatientInsightsService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Map<Long, Map<String, Object>> forPatients(Collection<Long> ids) {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        for (Long id : ids) out.put(id, new LinkedHashMap<>());
        LocalDateTime now = Dates.now();
        var params = new MapSqlParameterSource("ids", ids).addValue("now", now);

        jdbc.query("""
                select a.patient_id, a.start_at, a.reason, a.dentist_id from appointments a
                 where a.patient_id in (:ids) and a.status = 'ATENDIDA'
                   and a.start_at = (select max(x.start_at) from appointments x where x.patient_id = a.patient_id and x.status = 'ATENDIDA')
                """, params, rs -> {
            Map<String, Object> m = out.get(rs.getLong(1));
            if (m != null && !m.containsKey("lastVisit")) m.put("lastVisit", Map.of("startAt", rs.getTimestamp(2).toLocalDateTime(), "reason", rs.getString(3)));
        });
        jdbc.query("""
                select a.patient_id, a.start_at, a.reason, a.status, a.id from appointments a
                 where a.patient_id in (:ids) and a.start_at > :now and a.status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION')
                 order by a.start_at
                """, params, rs -> {
            Map<String, Object> m = out.get(rs.getLong(1));
            if (m != null && !m.containsKey("nextAppointment"))
                m.put("nextAppointment", Map.of("startAt", rs.getTimestamp(2).toLocalDateTime(), "reason", rs.getString(3), "status", rs.getString(4), "id", rs.getLong(5)));
        });
        jdbc.query("""
                select t.patient_id, t.id, t.kind, t.title, t.due_date, t.priority,
                       (select count(*) from tasks c where c.patient_id = t.patient_id and c.status = 'PENDIENTE') as n
                  from tasks t where t.patient_id in (:ids) and t.status = 'PENDIENTE'
                 order by t.due_date, t.due_time is null, t.due_time, t.id
                """, params, rs -> {
            Map<String, Object> m = out.get(rs.getLong(1));
            if (m != null && !m.containsKey("task")) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", rs.getLong(2));
                t.put("kind", rs.getString(3));
                t.put("title", rs.getString(4));
                t.put("dueDate", rs.getDate(5).toLocalDate());
                t.put("priority", rs.getString(6));
                t.put("overdue", rs.getDate(5).toLocalDate().isBefore(now.toLocalDate()));
                t.put("pending", rs.getLong(7));
                m.put("task", t);
            }
        });
        jdbc.query("""
                select tp.patient_id, coalesce(sum(ti.price), 0), coalesce(sum(case when ti.status = 'REALIZADO' then ti.price else 0 end), 0),
                       count(*), sum(case when ti.status = 'REALIZADO' then 1 else 0 end)
                  from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id
                 where tp.patient_id in (:ids) and tp.status = 'ACTIVO'
                 group by tp.patient_id
                """, params, rs -> {
            Map<String, Object> m = out.get(rs.getLong(1));
            if (m == null) return;
            BigDecimal total = rs.getBigDecimal(2);
            BigDecimal done = rs.getBigDecimal(3);
            long items = rs.getLong(4);
            long doneItems = rs.getLong(5);
            int pct = total.signum() > 0 ? done.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.DOWN).intValue()
                    : items > 0 ? (int) (doneItems * 100 / items) : 0;
            m.put("budget", Map.of("total", total, "done", done, "percent", pct, "items", items, "doneItems", doneItems));
        });
        jdbc.query("""
                select p.id,
                       coalesce((select sum(ti.price) from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id
                                  where tp.patient_id = p.id and ti.status = 'REALIZADO'), 0)
                     - coalesce((select sum(py.amount) from payments py where py.patient_id = p.id and py.status = 'VALIDO'), 0)
                  from patients p where p.id in (:ids)
                """, params, rs -> {
            Map<String, Object> m = out.get(rs.getLong(1));
            BigDecimal b = rs.getBigDecimal(2);
            if (m != null && b != null && b.compareTo(new BigDecimal("0.009")) > 0) m.put("balance", b.setScale(2, RoundingMode.HALF_UP));
        });
        return out;
    }
}
