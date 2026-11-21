package pe.clinicacardenas.commission;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Mis ganancias" del odontólogo que inició sesión: cuánto gana por cada tratamiento realizado, por día y por mes,
 * en soles (no solo el porcentaje), según sus reglas de comisión vigentes. Solo ve lo suyo.
 */
@RestController
public class MyEarningsController {

    private final CommissionService commissions;
    private final JdbcTemplate jdbc;

    public MyEarningsController(CommissionService commissions, JdbcTemplate jdbc) {
        this.commissions = commissions;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/me/earnings")
    @SuppressWarnings("unchecked")
    public Map<String, Object> earnings(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        List<Long> ids = jdbc.queryForList(
                "select d.id from dentists d join users u on u.id = d.user_id where lower(u.username) = lower(?)",
                Long.class, CurrentUser.usernameOrSystem());
        if (ids.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_ODONTOLOGOS", "Esta vista es para odontólogos.");
        LocalDate t = to == null ? Dates.today() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1) : from;
        Map<String, Object> c = commissions.calculate(ids.get(0), f, t);

        // Une lo pendiente de liquidar y lo ya liquidado: es lo que el odontólogo ganó en el período.
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> l : (List<Map<String, Object>>) c.get("lines")) { l.put("state", "POR_LIQUIDAR"); lines.add(l); }
        for (Map<String, Object> l : (List<Map<String, Object>>) c.get("alreadySettled")) { l.put("state", "LIQUIDADO"); lines.add(l); }
        lines.sort(Comparator.comparing(l -> (LocalDateTime) l.get("doneAt")));
        Map<String, BigDecimal> byDay = new TreeMap<>();
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal produced = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) {
            BigDecimal amount = (BigDecimal) l.get("commission");
            LocalDateTime d = (LocalDateTime) l.get("doneAt");
            byDay.merge(d.toLocalDate().toString(), amount, BigDecimal::add);
            byMonth.merge(d.toLocalDate().toString().substring(0, 7), amount, BigDecimal::add);
            total = total.add(amount);
            produced = produced.add((BigDecimal) l.get("price"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dentist", c.get("dentist"));
        out.put("from", f);
        out.put("to", t);
        out.put("total", total);
        out.put("produced", produced);
        out.put("treatments", lines.size());
        out.put("toSettle", c.get("total"));
        out.put("settled", c.get("alreadySettledTotal"));
        out.put("lines", lines);
        out.put("byDay", byDay.entrySet().stream().map(e -> Map.of("day", e.getKey(), "amount", e.getValue())).toList());
        out.put("byMonth", byMonth.entrySet().stream().map(e -> Map.of("month", e.getKey(), "amount", e.getValue())).toList());
        out.put("pendingCollection", c.get("pendingCollection"));
        out.put("withoutRule", c.get("withoutRule"));
        out.put("rules", c.get("rules"));
        return out;
    }
}
