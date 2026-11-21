package pe.clinicacardenas.messaging;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalTime;
import java.util.*;

/** v2.6: reglas de automatización (cuándo se envía cada aviso) editables por la clínica. */
@Service
public class AutomationService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final NotificationService notifications;

    public AutomationService(JdbcTemplate jdbc, AuditService audit, @Lazy NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifications = notifications;
    }

    public record RuleRequest(Boolean active, Integer amount, String unit, String sendTime) {}

    public List<AutomationRule> rules() {
        return jdbc.query("select code, name, kind, amount, unit, send_time, template_code, notif_type, active from automation_rules order by sort_order",
                (rs, i) -> new AutomationRule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        rs.getTime(6) == null ? null : rs.getTime(6).toLocalTime(), rs.getString(7), rs.getString(8), rs.getBoolean(9)));
    }

    public List<Map<String, Object>> view() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AutomationRule r : rules()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.code());
            m.put("name", r.name());
            m.put("kind", r.kind());
            m.put("amount", r.amount());
            m.put("unit", r.unit());
            m.put("sendTime", r.sendTime() == null ? null : r.sendTime().toString());
            m.put("templateCode", r.templateCode());
            m.put("active", r.active());
            m.put("summary", summary(r));
            m.put("sent30d", jdbc.queryForObject("select count(*) from notifications where template_code = ? and created_at >= ? and operation <> 'ANULAR'",
                    Integer.class, r.templateCode(), Dates.now().minusDays(30)));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> update(String code, RuleRequest r) {
        AutomationRule old = rules().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow(() -> ApiException.notFound("La automatización"));
        Map<String, String> e = new LinkedHashMap<>();
        int amount = r.amount() == null ? old.amount() : r.amount();
        String unit = r.unit() == null ? old.unit() : r.unit().trim().toUpperCase();
        if (!Set.of("HORAS", "DIAS").contains(unit)) e.put("unit", "Elige horas o días.");
        if ("ANTES".equals(old.kind()) && "HORAS".equals(unit) && (amount < 2 || amount > 72)) e.put("amount", "Entre 2 y 72 horas antes.");
        if ("ANTES".equals(old.kind()) && "DIAS".equals(unit) && (amount < 1 || amount > 15)) e.put("amount", "Entre 1 y 15 días antes.");
        if ("DESPUES".equals(old.kind()) && "HORAS".equals(unit) && (amount < 0 || amount > 72)) e.put("amount", "Entre 0 y 72 horas después.");
        if ("DESPUES".equals(old.kind()) && "DIAS".equals(unit) && (amount < 1 || amount > 30)) e.put("amount", "Entre 1 y 30 días después.");
        LocalTime time = old.sendTime();
        if (r.sendTime() != null && !r.sendTime().isBlank()) {
            try { time = LocalTime.parse(r.sendTime().length() == 5 ? r.sendTime() + ":00" : r.sendTime()); }
            catch (Exception ex) { e.put("sendTime", "Hora no válida."); }
            if (time != null && (time.isBefore(LocalTime.of(7, 0)) || time.isAfter(LocalTime.of(21, 0)))) e.put("sendTime", "Envía entre las 07:00 y las 21:00 para no molestar al paciente.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la automatización.", e);
        boolean active = r.active() == null ? old.active() : r.active();
        jdbc.update("update automation_rules set active=?, amount=?, unit=?, send_time=?, updated_at=?, updated_by=? where code=?",
                active, "DIA".equals(old.kind()) ? 0 : amount, "DIA".equals(old.kind()) ? "HORAS" : unit, time, Dates.now(), CurrentUser.usernameOrSystem(), code);
        AutomationRule now = rules().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
        audit.record("AUTOMATIZACION", null, "EDITAR", now.name() + ": " + (active ? summary(now) : "desactivada"));
        int moved = "DESPUES".equals(now.kind()) ? 0 : notifications.rescheduleFuture();
        Map<String, Object> m = new LinkedHashMap<>(view().stream().filter(x -> code.equals(x.get("code"))).findFirst().orElseThrow());
        m.put("rescheduled", moved);
        return m;
    }

    static String summary(AutomationRule r) {
        String t = r.sendTime() == null ? "" : " a las " + r.sendTime().toString().substring(0, 5);
        return switch (r.kind()) {
            case "ANTES" -> "DIAS".equals(r.unit()) ? r.amount() + (r.amount() == 1 ? " día" : " días") + " antes" + t
                    : r.amount() + " horas antes (si la cita es antes, 2 horas antes)";
            case "DIA" -> "El día de la cita" + t;
            case "DESPUES" -> "Al quedar atendida, " + ("DIAS".equals(r.unit()) ? r.amount() + (r.amount() == 1 ? " día" : " días") + " después" + t
                    : r.amount() == 0 ? "de inmediato" : r.amount() + " horas después");
            default -> r.kind();
        };
    }
}
