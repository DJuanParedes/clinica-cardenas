package pe.clinicacardenas.cash;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.SimplePdf;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * v2.10: cuadre y cierre de caja. Compara lo que el sistema espera por medio de pago (fondo + cobros − egresos del día)
 * con lo que el cajero cuenta (efectivo) y declara (Yape, Plin, tarjeta, transferencia según sus apps o vouchers).
 * Una caja cerrada no acepta cobros ni egresos manuales de ese día hasta que se reabra.
 */
@Service
public class CashClosingService {
    private static final String EFECTIVO = "EFECTIVO";
    private static final String NO_PUEDE_SER_NEGATIVO = "No puede ser negativo.";
    private static final String CLOSEDAT = "closedAt";
    private static final String COUNTEDCASH = "countedCash";
    private static final String DECLARED = "declared";
    private static final String DENOMINATIONS_2 = "denominations";
    private static final String DEPOSIT = "deposit";
    private static final String DIFFERENCE = "difference";
    private static final String EXPECTED = "expected";
    private static final String INCOME = "income";
    private static final String INCOMECOUNT = "incomeCount";
    private static final String LABEL = "label";
    private static final String METHOD = "method";
    private static final String METHODS_2 = "methods";
    private static final String NEXTFLOAT = "nextFloat";
    private static final String NOTES = "notes";
    private static final String OPENINGFLOAT = "openingFloat";
    private static final String OUTGO = "outgo";
    private static final String STATUS = "status";


    public static final List<BigDecimal> DENOMINATIONS = List.of(new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("50"),
            new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("1"),
            new BigDecimal("0.50"), new BigDecimal("0.20"), new BigDecimal("0.10"));
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMYH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CashService cash;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final String businessName;
    private final String ruc;

    public CashClosingService(CashService cash, JdbcTemplate jdbc, AuditService audit,
                              @Value("${app.company.business-name}") String businessName, @Value("${app.company.ruc}") String ruc) {
        this.cash = cash;
        this.jdbc = jdbc;
        this.audit = audit;
        this.businessName = businessName;
        this.ruc = ruc;
    }

    public record CloseRequest(LocalDate date, BigDecimal openingFloat, BigDecimal countedCash, Map<String, BigDecimal> declared,
                               Map<String, Integer> denominations, BigDecimal nextFloat, String notes) {}

    public record ReopenRequest(String reason) {}

    /** ¿La caja de ese día está cerrada? (los cobros y egresos manuales de ese día se rechazan). */
    public boolean isClosed(LocalDate day) {
        Integer n = jdbc.queryForObject("select count(*) from cash_closings where day = ? and status = 'CERRADA'", Integer.class, day);
        return n != null && n > 0;
    }

    public void ensurePaymentDayOpen(Long paymentId) {
        List<LocalDateTime> l = jdbc.queryForList("select paid_at from payments where id = ?", LocalDateTime.class, paymentId);
        if (!l.isEmpty()) ensureOpen(l.get(0).toLocalDate());
    }

    public void ensureExpenseDayOpen(Long expenseId) {
        List<LocalDate> l = jdbc.queryForList("select expense_date from expenses where id = ?", LocalDate.class, expenseId);
        if (!l.isEmpty()) ensureOpen(l.get(0));
    }

    public void ensureOpen(LocalDate day) {
        if (isClosed(day)) throw ApiException.conflict("CAJA_CERRADA",
                "La caja del " + day.format(DMY) + " ya se cerró. Si hace falta registrar algo, pide al administrador que la reabra.");
    }

    /** Lo que debería haber hoy en cada medio de pago y, si ya se cerró, lo que se contó. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(LocalDate day) {
        if (day == null) day = Dates.today();
        if (day.isAfter(Dates.today())) throw ApiException.field("date", "La fecha no puede ser futura.");
        Map<String, Object> rep = cash.report(day, day);
        Map<String, Object> closing = find(day);
        BigDecimal opening = closing != null ? (BigDecimal) closing.get(OPENINGFLOAT) : suggestedFloat(day);
        Map<String, BigDecimal[]> by = new LinkedHashMap<>();
        for (String m : CashService.METHODS) by.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) rep.get("byMethod");
        for (Map<String, Object> m : methods) by.put((String) m.get(METHOD), new BigDecimal[]{(BigDecimal) m.get(INCOME), (BigDecimal) m.get(OUTGO)});
        List<Map<String, Object>> lines = new ArrayList<>();
        for (var e : by.entrySet()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put(METHOD, e.getKey());
            l.put(LABEL, CashService.METHOD_LABEL.get(e.getKey()));
            l.put(INCOME, money(e.getValue()[0]));
            l.put(OUTGO, money(e.getValue()[1]));
            BigDecimal exp = e.getValue()[0].subtract(e.getValue()[1]);
            if (EFECTIVO.equals(e.getKey())) exp = exp.add(opening);
            l.put(EXPECTED, money(exp));
            lines.add(l);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day);
        out.put(OPENINGFLOAT, money(opening));
        out.put(INCOME, rep.get(INCOME));
        out.put(OUTGO, rep.get(OUTGO));
        out.put(INCOMECOUNT, rep.get(INCOMECOUNT));
        out.put("outgoCount", rep.get("outgoCount"));
        out.put("voided", rep.get("voided"));
        out.put(METHODS_2, lines);
        out.put(DENOMINATIONS_2, DENOMINATIONS);
        out.put("closing", closing);
        if (closing != null) {
            Long after = jdbc.queryForObject("select count(*) from payments where status = 'VALIDO' and paid_at > ? and paid_at < ?",
                    Long.class, closing.get(CLOSEDAT), day.plusDays(1).atStartOfDay());
            out.put("paymentsAfterClose", after);
        }
        out.put("pendingDays", pendingDays());
        return out;
    }

    @Transactional
    public Map<String, Object> close(CloseRequest r) {
        LocalDate day = r.date() == null ? Dates.today() : r.date();
        if (day.isAfter(Dates.today())) throw ApiException.field("date", "La fecha no puede ser futura.");
        Map<String, Object> existing = find(day);
        if (existing != null && "CERRADA".equals(existing.get(STATUS)))
            throw ApiException.conflict("YA_CERRADA", "La caja de ese día ya está cerrada.");
        Map<String, String> e = new LinkedHashMap<>();
        BigDecimal opening = nn(r.openingFloat(), OPENINGFLOAT, e);
        BigDecimal counted;
        Map<String, Integer> denoms = new LinkedHashMap<>();
        if (r.denominations() != null && r.denominations().values().stream().anyMatch(v -> v != null && v > 0)) {
            counted = BigDecimal.ZERO;
            for (var d : r.denominations().entrySet()) {
                if (d.getValue() == null || d.getValue() == 0) continue;
                if (d.getValue() < 0) { e.put(DENOMINATIONS_2, "Las cantidades no pueden ser negativas."); break; }
                BigDecimal den = new BigDecimal(d.getKey());
                if (DENOMINATIONS.stream().noneMatch(x -> x.compareTo(den) == 0)) { e.put(DENOMINATIONS_2, "Denominación no válida: " + d.getKey()); break; }
                counted = counted.add(den.multiply(BigDecimal.valueOf(d.getValue())));
                denoms.put(den.stripTrailingZeros().toPlainString(), d.getValue());
            }
            if (r.countedCash() != null && r.countedCash().setScale(2, RoundingMode.HALF_UP).compareTo(counted.setScale(2, RoundingMode.HALF_UP)) != 0)
                e.put(COUNTEDCASH, "El total no coincide con el conteo de billetes y monedas (S/ " + money(counted) + ").");
        } else {
            counted = r.countedCash();
            if (counted == null) e.put(COUNTEDCASH, "Ingresa el efectivo que contaste.");
            else if (counted.signum() < 0) e.put(COUNTEDCASH, NO_PUEDE_SER_NEGATIVO);
        }
        BigDecimal next = r.nextFloat() == null ? BigDecimal.ZERO : r.nextFloat();
        if (next.signum() < 0) e.put(NEXTFLOAT, NO_PUEDE_SER_NEGATIVO);
        if (counted != null && next.compareTo(counted) > 0) e.put(NEXTFLOAT, "No puedes dejar más de lo que hay en caja.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa el cuadre.", e);

        // Recalcula lo esperado con el fondo indicado (no se confía en lo que manda el navegador).
        Map<String, Object> rep = cash.report(day, day);
        Map<String, BigDecimal> expected = new LinkedHashMap<>();
        for (String m : CashService.METHODS) expected.put(m, BigDecimal.ZERO);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> by = (List<Map<String, Object>>) rep.get("byMethod");
        for (Map<String, Object> m : by) expected.put((String) m.get(METHOD), ((BigDecimal) m.get("net")));
        expected.put(EFECTIVO, expected.get(EFECTIVO).add(opening));
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal diff = BigDecimal.ZERO;
        for (var x : expected.entrySet()) {
            BigDecimal other = r.declared() == null ? null : r.declared().get(x.getKey());
            if (other == null) other = x.getValue();
            BigDecimal declared = EFECTIVO.equals(x.getKey()) ? counted : other;
            // Un medio distinto al efectivo puede quedar en negativo (se pagaron egresos por Yape o transferencia).
            BigDecimal d = declared.subtract(x.getValue());
            diff = diff.add(d);
            lines.add(Map.of(METHOD, x.getKey(), LABEL, CashService.METHOD_LABEL.get(x.getKey()),
                    EXPECTED, money(x.getValue()), DECLARED, money(declared), DIFFERENCE, money(d)));
        }
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        if (diff.signum() != 0 && (notes == null || notes.length() < 5))
            throw ApiException.field(NOTES, "Hay una diferencia de S/ " + money(diff) + ": explica el motivo (sobrante, faltante, vuelto…).");
        String json;
        String denJson;
        try {
            json = JSON.writeValueAsString(lines);
            denJson = denoms.isEmpty() ? null : JSON.writeValueAsString(denoms);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        LocalDateTime now = Dates.now();
        Object[] args = {opening.setScale(2, RoundingMode.HALF_UP), expected.get(EFECTIVO).setScale(2, RoundingMode.HALF_UP),
                counted.setScale(2, RoundingMode.HALF_UP), diff.setScale(2, RoundingMode.HALF_UP), next.setScale(2, RoundingMode.HALF_UP),
                counted.subtract(next).setScale(2, RoundingMode.HALF_UP), json, denJson, rep.get(INCOME), rep.get(OUTGO),
                ((Number) rep.get(INCOMECOUNT)).intValue(), notes == null ? null : notes.substring(0, Math.min(500, notes.length())), now,
                CurrentUser.usernameOrSystem()};
        if (existing == null) {
            jdbc.update("""
                    insert into cash_closings (opening_float, expected_cash, counted_cash, difference, next_float, deposit, methods_json,
                      denominations_json, income, outgo, payments_count, notes, closed_at, closed_by, day, status)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'CERRADA')
                    """, append(args, day));
        } else {
            jdbc.update("""
                    update cash_closings set opening_float = ?, expected_cash = ?, counted_cash = ?, difference = ?, next_float = ?, deposit = ?,
                      methods_json = ?, denominations_json = ?, income = ?, outgo = ?, payments_count = ?, notes = ?, closed_at = ?, closed_by = ?,
                      status = 'CERRADA' where day = ?
                    """, append(args, day));
        }
        audit.register("CAJA", null, "CIERRE", "Caja del " + day.format(DMY) + " · contado S/ " + money(counted) + " · diferencia S/ " + money(diff));
        return summary(day);
    }

    @Transactional
    public Map<String, Object> reopen(LocalDate day, ReopenRequest r) {
        Map<String, Object> c = find(day);
        if (c == null || !"CERRADA".equals(c.get(STATUS))) throw ApiException.badRequest("NO_CERRADA", "Esa caja no está cerrada.");
        String reason = r == null || r.reason() == null ? "" : r.reason().trim();
        if (reason.length() < 5) throw ApiException.field("reason", "Explica por qué se reabre la caja.");
        jdbc.update("update cash_closings set status = 'REABIERTA', reopened_at = ?, reopened_by = ?, reopen_reason = ? where day = ?",
                Dates.now(), CurrentUser.usernameOrSystem(), reason.substring(0, Math.min(300, reason.length())), day);
        audit.register("CAJA", null, "REAPERTURA", "Caja del " + day.format(DMY) + " · " + reason);
        return summary(day);
    }

    public List<Map<String, Object>> history(int limit) {
        List<LocalDate> days = jdbc.queryForList("select day from cash_closings order by day desc limit ?", LocalDate.class, Math.clamp(limit, 1, 120));
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d : days) out.add(find(d));
        return out;
    }

    /** Días anteriores con movimientos que todavía no se cerraron (últimos 14 días). */
    public List<LocalDate> pendingDays() {
        LocalDate today = Dates.today();
        return jdbc.queryForList("""
                select distinct d from (
                  select date(paid_at) d from payments where status = 'VALIDO' and paid_at >= ? and paid_at < ?
                  union select expense_date d from expenses where status = 'VALIDO' and expense_date >= ? and expense_date < ?) x
                 where d not in (select day from cash_closings where status = 'CERRADA') order by d
                """, LocalDate.class, today.minusDays(14).atStartOfDay(), today.atStartOfDay(), today.minusDays(14), today);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(LocalDate day) {
        Map<String, Object> s = summary(day);
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) s.get("closing");
        if (c == null) throw ApiException.notFound("El cierre de ese día");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) c.get(METHODS_2);
        List<String[]> rows = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            if (((BigDecimal) l.get(EXPECTED)).signum() == 0 && ((BigDecimal) l.get(DECLARED)).signum() == 0) continue;
            rows.add(new String[]{String.valueOf(l.get(LABEL)), "S/ " + l.get(EXPECTED), "S/ " + l.get(DECLARED), "S/ " + l.get(DIFFERENCE)});
        }
        SimplePdf p = new SimplePdf().header(businessName, "RUC " + ruc)
                .title("CUADRE Y CIERRE DE CAJA · " + day.format(DMY))
                .paragraph("Cerrada por " + c.get("closedBy") + " el " + ((LocalDateTime) c.get(CLOSEDAT)).format(DMYH)
                        + ("REABIERTA".equals(c.get(STATUS)) ? " · REABIERTA por " + c.get("reopenedBy") + ": " + c.get("reopenReason") : ""), 10, false)
                .space(6)
                .paragraph("Fondo inicial: S/ " + c.get(OPENINGFLOAT) + "     Cobros: S/ " + c.get(INCOME) + " (" + c.get("paymentsCount")
                        + ")     Egresos: S/ " + c.get(OUTGO), 10, false)
                .space(6)
                .table(new String[]{"Medio", "Esperado", "Contado / declarado", "Diferencia"}, new float[]{0.31f, 0.23f, 0.23f, 0.23f}, rows,
                        new char[]{'L', 'R', 'R', 'R'}, 10)
                .space(6)
                .paragraph("Diferencia total: S/ " + c.get(DIFFERENCE) + "     Fondo para mañana: S/ " + c.get(NEXTFLOAT)
                        + "     Efectivo a depositar: S/ " + c.get(DEPOSIT), 10, true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> den = (Map<String, Integer>) c.get(DENOMINATIONS_2);
        if (den != null && !den.isEmpty()) {
            StringBuilder sb = new StringBuilder("Conteo de efectivo: ");
            den.forEach((k, v) -> sb.append(v).append(" × S/ ").append(k).append("   "));
            p.paragraph(sb.toString().trim(), 9, false);
        }
        if (c.get(NOTES) != null) p.paragraph("Observaciones: " + c.get(NOTES), 10, false);
        p.space(30).paragraph("______________________________          ______________________________", 10, false)
                .paragraph("Firma de quien cierra                                 Firma de quien recibe", 9, false);
        return p.build();
    }

    // ------------------------------------------------------------------ internos

    private Map<String, Object> find(LocalDate day) {
        List<Map<String, Object>> l = jdbc.query("select * from cash_closings where day = ?", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("day", rs.getObject("day", LocalDate.class));
            m.put(STATUS, rs.getString(STATUS));
            m.put(OPENINGFLOAT, rs.getBigDecimal("opening_float"));
            m.put("expectedCash", rs.getBigDecimal("expected_cash"));
            m.put(COUNTEDCASH, rs.getBigDecimal("counted_cash"));
            m.put(DIFFERENCE, rs.getBigDecimal(DIFFERENCE));
            m.put(NEXTFLOAT, rs.getBigDecimal("next_float"));
            m.put(DEPOSIT, rs.getBigDecimal(DEPOSIT));
            m.put(INCOME, rs.getBigDecimal(INCOME));
            m.put(OUTGO, rs.getBigDecimal(OUTGO));
            m.put("paymentsCount", rs.getInt("payments_count"));
            m.put(NOTES, rs.getString(NOTES));
            m.put(CLOSEDAT, rs.getObject("closed_at", LocalDateTime.class));
            m.put("closedBy", rs.getString("closed_by"));
            m.put("reopenedAt", rs.getObject("reopened_at", LocalDateTime.class));
            m.put("reopenedBy", rs.getString("reopened_by"));
            m.put("reopenReason", rs.getString("reopen_reason"));
            try {
                m.put(METHODS_2, JSON.readValue(rs.getString("methods_json"), new TypeReference<List<Map<String, Object>>>() {})
                        .stream().map(CashClosingService::toMoney).toList());
                String den = rs.getString("denominations_json");
                m.put(DENOMINATIONS_2, den == null ? null : JSON.readValue(den, new TypeReference<LinkedHashMap<String, Integer>>() {}));
            } catch (Exception ex) {
                m.put(METHODS_2, List.of());
            }
            return m;
        }, day);
        return l.isEmpty() ? null : l.get(0);
    }

    private static Map<String, Object> toMoney(Map<String, Object> l) {
        Map<String, Object> m = new LinkedHashMap<>(l);
        for (String k : List.of(EXPECTED, DECLARED, DIFFERENCE)) m.put(k, money(new BigDecimal(String.valueOf(l.get(k)))));
        return m;
    }

    /** Fondo sugerido: lo que se dejó en el último cierre o, si no hay, el fondo configurado. */
    private BigDecimal suggestedFloat(LocalDate day) {
        List<BigDecimal> l = jdbc.queryForList("select next_float from cash_closings where day < ? and status = 'CERRADA' order by day desc limit 1",
                BigDecimal.class, day);
        if (!l.isEmpty()) return l.get(0);
        List<String> s = jdbc.queryForList("select v from app_settings where k = 'caja_fondo_inicial'", String.class);
        try {
            return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s.get(0));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal nn(BigDecimal v, String field, Map<String, String> e) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) e.put(field, NO_PUEDE_SER_NEGATIVO);
        return v;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static Object[] append(Object[] a, Object x) {
        Object[] r = Arrays.copyOf(a, a.length + 1);
        r[a.length] = x;
        return r;
    }
}
