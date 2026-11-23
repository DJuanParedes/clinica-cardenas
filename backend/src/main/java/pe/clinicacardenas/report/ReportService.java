package pe.clinicacardenas.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** HU-19 ingresos por período · HU-20 citas por estado · HU-21 tratamientos realizados y pendientes. */
@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final String ATENDIDA = "ATENDIDA";
    private static final String CANCELADA = "CANCELADA";
    private static final String NO_ASISTIO = "NO_ASISTIO";
    private static final String AMOUNT = "amount";
    private static final String ATTENDED = "attended";
    private static final String BYDENTIST = "byDentist";
    private static final String CATEGORY = "category";
    private static final String COUNT = "count";
    private static final String DENTIST_ID = "dentist_id";
    private static final String MONTH = "month";
    private static final String PATIENT = "patient";
    private static final String PENDING = "pending";
    private static final String PRICE = "price";
    private static final String PROCEDURE_NAME = "procedure_name";
    private static final String REASON = "reason";
    private static final String STATUS = "status";
    private static final String STATUS_REASON = "status_reason";
    private static final String TOTAL = "total";


    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static BigDecimal money(Object v) {
        BigDecimal b = v == null ? BigDecimal.ZERO : v instanceof BigDecimal x ? x : new BigDecimal(v.toString());
        return b.setScale(2, RoundingMode.HALF_UP);
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static void check(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("PERIODO", "Indica el período.");
        if (to.isBefore(from)) throw ApiException.field("to", "La fecha final no puede ser anterior a la inicial.");
        if (ChronoUnit.DAYS.between(from, to) > 731) throw ApiException.field("to", "El período no puede superar dos años.");
    }

    /** Agrupación automática: por día hasta 45 días, por semana hasta 6 meses, luego por mes. */
    public static String grouping(LocalDate from, LocalDate to, String requested) {
        if (requested != null && List.of("day", "week", MONTH).contains(requested)) return requested;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return days <= 45 ? "day" : days <= 186 ? "week" : MONTH;
    }

    public static LocalDate bucket(LocalDate d, String g) {
        return switch (g) {
            case "week" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    static LinkedHashMap<LocalDate, long[]> emptyCounts(LocalDate from, LocalDate to, String g, int width) {
        LinkedHashMap<LocalDate, long[]> m = new LinkedHashMap<>();
        for (LocalDate d = bucket(from, g); !d.isAfter(to); d = switch (g) { case "week" -> d.plusWeeks(1); case MONTH -> d.plusMonths(1); default -> d.plusDays(1); }) {
            m.put(d, new long[width]);
        }
        return m;
    }

    private static String labelFor(LocalDate d, String g) {
        String[] months = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic"};
        return switch (g) {
            case MONTH -> months[d.getMonthValue() - 1] + " " + String.valueOf(d.getYear()).substring(2);
            case "week" -> d.getDayOfMonth() + " " + months[d.getMonthValue() - 1];
            default -> d.getDayOfMonth() + " " + months[d.getMonthValue() - 1];
        };
    }

    // ------------------------------------------------------------------ HU-19 ingresos

    public Map<String, Object> income(LocalDate from, LocalDate to, String group) {
        check(from, to);
        String g = grouping(from, to, group);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select py.paid_at, py.amount, py.method, pr.category,
                       coalesce(ti.done_by_dentist_id, tp.dentist_id) as dentist_id
                  from payments py
                  left join treatment_items ti on ti.id = py.treatment_item_id
                  left join treatment_plans tp on tp.id = ti.plan_id
                  left join procedures pr on pr.id = ti.procedure_id
                 where py.status = 'VALIDO' and py.paid_at >= ? and py.paid_at < ?
                """, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<Long, String> dentistNames = dentistNames();
        BigDecimal total = BigDecimal.ZERO;
        LinkedHashMap<LocalDate, long[]> series = emptyCounts(from, to, g, 1); // céntimos
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> byDentist = new LinkedHashMap<>();
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            BigDecimal a = money(r.get(AMOUNT));
            total = total.add(a);
            LocalDate day = Dates.toLocalDate(r.get("paid_at"));
            series.computeIfAbsent(bucket(day, g), k -> new long[1])[0] += a.movePointRight(2).longValue();
            byMethod.merge(CashService.METHOD_LABEL.getOrDefault((String) r.get("method"), (String) r.get("method")), a, BigDecimal::add);
            Object did = r.get(DENTIST_ID);
            byDentist.merge(did == null ? "Pagos a cuenta" : dentistNames.getOrDefault(((Number) did).longValue(), "—"), a, BigDecimal::add);
            byCategory.merge(r.get(CATEGORY) == null ? "A cuenta" : (String) r.get(CATEGORY), a, BigDecimal::add);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate pFrom = from.minusDays(days);
        LocalDate pTo = from.minusDays(1);
        BigDecimal previous = money(jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where status = 'VALIDO' and paid_at >= ? and paid_at < ?",
                BigDecimal.class, pFrom.atStartOfDay(), pTo.plusDays(1).atStartOfDay()));
        BigDecimal invoiced = money(jdbc.queryForObject("""
                select coalesce(sum(total_amount), 0) from invoices
                 where status in ('ACEPTADO', 'OBSERVADO') and issue_date >= ? and issue_date <= ?
                """, BigDecimal.class, from, to));
        BigDecimal expenses = money(jdbc.queryForObject("select coalesce(sum(amount), 0) from expenses where status = 'VALIDO' and expense_date >= ? and expense_date <= ?",
                BigDecimal.class, from, to));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("grouping", g);
        m.put(TOTAL, money(total));
        m.put(COUNT, rows.size());
        m.put("averageTicket", rows.isEmpty() ? BigDecimal.ZERO : money(total.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP)));
        m.put("previousTotal", previous);
        m.put("previousFrom", pFrom);
        m.put("previousTo", pTo);
        m.put("variation", previous.signum() == 0 ? null : total.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP));
        m.put("invoiced", invoiced);
        m.put("expenses", expenses);
        m.put("net", money(total.subtract(expenses)));
        List<Map<String, Object>> s = new ArrayList<>();
        series.forEach((k, v) -> s.add(Map.of("key", k, "label", labelFor(k, g), AMOUNT, BigDecimal.valueOf(v[0], 2))));
        m.put("series", s);
        m.put("byMethod", ranked(byMethod, total));
        m.put(BYDENTIST, ranked(byDentist, total));
        m.put("byCategory", ranked(byCategory, total));
        return m;
    }

    private static List<Map<String, Object>> ranked(Map<String, BigDecimal> src, BigDecimal total) {
        List<Map<String, Object>> out = new ArrayList<>();
        src.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).forEach(e -> out.add(Map.of(
                "name", e.getKey(), AMOUNT, money(e.getValue()),
                "share", total.signum() == 0 ? 0 : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP))));
        return out;
    }

    private Map<Long, String> dentistNames() {
        Map<Long, String> m = new HashMap<>();
        jdbc.query("select d.id, u.full_name from dentists d join users u on u.id = d.user_id", rs -> { m.put(rs.getLong(1), rs.getString(2)); });
        return m;
    }

    // ------------------------------------------------------------------ HU-20 citas

    private static final List<String> STATUSES = List.of("PROGRAMADA", "CONFIRMADA", "EN_ESPERA", "EN_ATENCION", ATENDIDA, CANCELADA, NO_ASISTIO);

    public Map<String, Object> appointments(LocalDate from, LocalDate to, Long dentistId, String group) {
        check(from, to);
        String g = grouping(from, to, group);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select a.id, a.start_at, a.status, a.status_reason, a.reason, a.dentist_id, a.duration_min,
                       concat(p.first_name, ' ', p.last_name) as patient, p.id as patient_id
                  from appointments a join patients p on p.id = a.patient_id
                 where a.start_at >= ? and a.start_at < ? and (? is null or a.dentist_id = ?)
                 order by a.start_at
                """, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), dentistId, dentistId);
        Map<Long, String> names = dentistNames();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        STATUSES.forEach(s -> byStatus.put(s, 0L));
        LinkedHashMap<LocalDate, long[]> series = emptyCounts(from, to, g, 3); // atendidas, perdidas, por atender
        Map<Long, long[]> byDentist = new LinkedHashMap<>(); // total, atendidas, canceladas, no asistió
        Map<String, Long> reasons = new HashMap<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        long minutes = 0;
        for (Map<String, Object> r : rows) {
            String st = (String) r.get(STATUS);
            byStatus.merge(st, 1L, Long::sum);
            LocalDateTime start = Dates.toLocalDateTime(r.get("start_at"));
            long[] b = series.computeIfAbsent(bucket(start.toLocalDate(), g), k -> new long[3]);
            int idx = ATENDIDA.equals(st) ? 0 : (CANCELADA.equals(st) || NO_ASISTIO.equals(st)) ? 1 : 2;
            b[idx]++;
            long did = ((Number) r.get(DENTIST_ID)).longValue();
            long[] d = byDentist.computeIfAbsent(did, k -> new long[4]);
            d[0]++;
            if (ATENDIDA.equals(st)) { d[1]++; minutes += ((Number) r.get("duration_min")).longValue(); }
            if (CANCELADA.equals(st)) d[2]++;
            if (NO_ASISTIO.equals(st)) d[3]++;
            if (CANCELADA.equals(st) && r.get(STATUS_REASON) != null) {
                String reason = ((String) r.get(STATUS_REASON)).trim();
                if (!reason.startsWith("Reprogramada")) reasons.merge(reason.length() > 60 ? reason.substring(0, 60) + "…" : reason, 1L, Long::sum);
            }
            if (detail.size() < 400) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", r.get("id"));
                x.put("startAt", start);
                x.put("patientId", r.get("patient_id"));
                x.put(PATIENT, r.get(PATIENT));
                x.put("dentist", names.get(did));
                x.put(REASON, r.get(REASON));
                x.put(STATUS, st);
                x.put("statusReason", r.get(STATUS_REASON));
                detail.add(x);
            }
        }
        long total = rows.size();
        long attended = byStatus.get(ATENDIDA);
        long cancelled = byStatus.get(CANCELADA);
        long noShow = byStatus.get(NO_ASISTIO);
        long scheduled = byStatus.get("PROGRAMADA") + byStatus.get("CONFIRMADA") + byStatus.get("EN_ESPERA") + byStatus.get("EN_ATENCION");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("grouping", g);
        m.put(TOTAL, total);
        m.put("scheduled", scheduled);
        m.put(ATTENDED, attended);
        m.put("cancelled", cancelled);
        m.put("noShow", noShow);
        m.put("attendanceRate", pct(attended, attended + noShow));
        m.put("cancellationRate", pct(cancelled, total));
        m.put("hoursAttended", Math.round(minutes / 6.0) / 10.0);
        List<Map<String, Object>> st = new ArrayList<>();
        byStatus.forEach((k, v) -> st.add(Map.of(STATUS, k, COUNT, v, "share", pct(v, total))));
        m.put("byStatus", st);
        List<Map<String, Object>> s = new ArrayList<>();
        series.forEach((k, v) -> s.add(Map.of("key", k, "label", labelFor(k, g), ATTENDED, v[0], "lost", v[1], "scheduled", v[2])));
        m.put("series", s);
        List<Map<String, Object>> bd = new ArrayList<>();
        byDentist.forEach((k, v) -> bd.add(Map.of("dentistId", k, "name", names.getOrDefault(k, "—"), TOTAL, v[0], ATTENDED, v[1],
                "cancelled", v[2], "noShow", v[3], "attendanceRate", pct(v[1], v[1] + v[3]))));
        m.put(BYDENTIST, bd);
        List<Map<String, Object>> rs = new ArrayList<>();
        reasons.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(6)
                .forEach(e -> rs.add(Map.of(REASON, e.getKey(), COUNT, e.getValue())));
        m.put("cancelReasons", rs);
        m.put("detail", detail);
        return m;
    }

    // ------------------------------------------------------------------ HU-21 tratamientos

    public Map<String, Object> treatments(LocalDate from, LocalDate to, Long dentistId) {
        check(from, to);
        Map<Long, String> names = dentistNames();
        List<Map<String, Object>> done = jdbc.queryForList("""
                select ti.id, ti.price, ti.done_at, ti.tooth, pr.name as procedure_name, pr.category,
                       coalesce(ti.done_by_dentist_id, tp.dentist_id) as dentist_id,
                       concat(p.first_name, ' ', p.last_name) as patient, p.id as patient_id
                  from treatment_items ti
                  join treatment_plans tp on tp.id = ti.plan_id
                  join patients p on p.id = tp.patient_id
                  join procedures pr on pr.id = ti.procedure_id
                 where ti.status = 'REALIZADO' and ti.done_at >= ? and ti.done_at < ?
                   and (? is null or coalesce(ti.done_by_dentist_id, tp.dentist_id) = ?)
                 order by ti.done_at desc
                """, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), dentistId, dentistId);
        List<Map<String, Object>> pending = jdbc.queryForList("""
                select ti.id, ti.price, ti.status, ti.tooth, tp.created_at, pr.name as procedure_name, pr.category,
                       tp.dentist_id, concat(p.first_name, ' ', p.last_name) as patient, p.id as patient_id, tp.title
                  from treatment_items ti
                  join treatment_plans tp on tp.id = ti.plan_id
                  join patients p on p.id = tp.patient_id
                  join procedures pr on pr.id = ti.procedure_id
                 where ti.status in ('PENDIENTE', 'EN_PROCESO') and tp.status = 'ACTIVO' and tp.created_at < ?
                   and (? is null or tp.dentist_id = ?)
                 order by tp.created_at
                """, to.plusDays(1).atStartOfDay(), dentistId, dentistId);

        Map<String, Object[]> byProc = new TreeMap<>(); // nombre -> [categoría, realizados, monto realizado, pendientes, monto pendiente]
        Map<String, long[]> byDentist = new LinkedHashMap<>(); // realizados, pendientes
        BigDecimal doneAmount = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        List<Map<String, Object>> doneDetail = new ArrayList<>();
        List<Map<String, Object>> pendingDetail = new ArrayList<>();
        for (Map<String, Object> r : done) {
            BigDecimal price = money(r.get(PRICE));
            doneAmount = doneAmount.add(price);
            Object[] p = byProc.computeIfAbsent((String) r.get(PROCEDURE_NAME), k -> new Object[]{r.get(CATEGORY), 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO});
            p[1] = (Long) p[1] + 1;
            p[2] = ((BigDecimal) p[2]).add(price);
            String dn = r.get(DENTIST_ID) == null ? "Sin asignar" : names.getOrDefault(((Number) r.get(DENTIST_ID)).longValue(), "—");
            byDentist.computeIfAbsent(dn, k -> new long[2])[0]++;
            if (doneDetail.size() < 300) doneDetail.add(row(r, dn, Dates.toLocalDateTime(r.get("done_at")), "REALIZADO"));
        }
        for (Map<String, Object> r : pending) {
            BigDecimal price = money(r.get(PRICE));
            pendingAmount = pendingAmount.add(price);
            Object[] p = byProc.computeIfAbsent((String) r.get(PROCEDURE_NAME), k -> new Object[]{r.get(CATEGORY), 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO});
            p[3] = (Long) p[3] + 1;
            p[4] = ((BigDecimal) p[4]).add(price);
            String dn = r.get(DENTIST_ID) == null ? "Sin asignar" : names.getOrDefault(((Number) r.get(DENTIST_ID)).longValue(), "—");
            byDentist.computeIfAbsent(dn, k -> new long[2])[1]++;
            if (pendingDetail.size() < 300) {
                Map<String, Object> x = row(r, dn, Dates.toLocalDateTime(r.get("created_at")), (String) r.get(STATUS));
                x.put("plan", r.get("title"));
                pendingDetail.add(x);
            }
        }
        long total = (long) done.size() + pending.size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("doneCount", done.size());
        m.put("doneAmount", money(doneAmount));
        m.put("pendingCount", pending.size());
        m.put("pendingAmount", money(pendingAmount));
        m.put("completionRate", pct(done.size(), total));
        m.put("activePlans", jdbc.queryForObject("select count(*) from treatment_plans where status = 'ACTIVO' and (? is null or dentist_id = ?)", Long.class, dentistId, dentistId));
        m.put("completedPlans", jdbc.queryForObject("""
                select count(*) from treatment_plans tp where tp.status = 'COMPLETADO' and (? is null or tp.dentist_id = ?)
                   and exists (select 1 from treatment_items ti where ti.plan_id = tp.id and ti.done_at >= ? and ti.done_at < ?)
                """, Long.class, dentistId, dentistId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        List<Map<String, Object>> procs = new ArrayList<>();
        byProc.forEach((k, v) -> procs.add(Map.of("procedure", k, CATEGORY, v[0], "done", v[1], "doneAmount", money(v[2]),
                PENDING, v[3], "pendingAmount", money(v[4]))));
        procs.sort((a, b) -> Long.compare((Long) b.get("done") + (Long) b.get(PENDING), (Long) a.get("done") + (Long) a.get(PENDING)));
        m.put("byProcedure", procs);
        List<Map<String, Object>> ds = new ArrayList<>();
        byDentist.forEach((k, v) -> ds.add(Map.of("name", k, "done", v[0], PENDING, v[1])));
        m.put(BYDENTIST, ds);
        m.put("doneDetail", doneDetail);
        m.put("pendingDetail", pendingDetail);
        return m;
    }

    private static Map<String, Object> row(Map<String, Object> r, String dentist, LocalDateTime date, String status) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("id", r.get("id"));
        x.put("date", date);
        x.put("patientId", r.get("patient_id"));
        x.put(PATIENT, r.get(PATIENT));
        x.put("procedure", r.get(PROCEDURE_NAME) + (r.get("tooth") == null ? "" : " · pieza " + r.get("tooth")));
        x.put(CATEGORY, r.get(CATEGORY));
        x.put("dentist", dentist);
        x.put(PRICE, money(r.get(PRICE)));
        x.put(STATUS, status);
        return x;
    }
}
