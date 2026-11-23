package pe.clinicacardenas.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.SimplePdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * v2.8: reporte anual de la clínica, para descargar en PDF o CSV. Compara con el año anterior:
 * ingresos y egresos, pacientes nuevos y atendidos, citas, procedimientos, odontólogos, medios de pago,
 * fuentes de captación, satisfacción y embudo de ventas.
 */
@Service
public class AnnualReportService {

    private static final String[] MES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"};
    private static final Map<String, String> METHOD = Map.of("EFECTIVO", "Efectivo", "TARJETA", "Tarjeta", "YAPE", "Yape", "PLIN", "Plin", "TRANSFERENCIA", "Transferencia");

    private final JdbcTemplate jdbc;
    private final String clinic, address, ruc;

    public AnnualReportService(JdbcTemplate jdbc, @Value("${app.company.trade-name}") String clinic,
                               @Value("${app.company.address:}") String address, @Value("${app.company.ruc:}") String ruc) {
        this.jdbc = jdbc;
        this.clinic = clinic;
        this.address = address;
        this.ruc = ruc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(int year) {
        int now = Dates.today().getYear();
        if (year < 2020 || year > now) throw ApiException.field("year", "Elige un año entre 2020 y " + now + ".");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("year", year);
        r.put("generatedAt", Dates.now());
        r.put("partial", year == now);
        Map<String, Object> cur = summary(year), prev = summary(year - 1);
        r.put("summary", cur);
        r.put("previous", prev);
        r.put("months", months(year));
        LocalDateTime a = LocalDate.of(year, 1, 1).atStartOfDay(), b = a.plusYears(1);
        r.put("procedures", jdbc.queryForList("""
                select pr.name, count(*) as times, sum(ti.price) as amount from treatment_items ti join procedures pr on pr.id = ti.procedure_id
                 where ti.status = 'REALIZADO' and ti.done_at >= ? and ti.done_at < ? group by pr.name order by times desc, amount desc limit 15
                """, a, b));
        r.put("dentists", jdbc.queryForList("""
                select u.full_name as name, count(distinct ap.id) as appointments,
                       (select coalesce(sum(ti.price), 0) from treatment_items ti where ti.done_by_dentist_id = d.id and ti.status = 'REALIZADO'
                          and ti.done_at >= ? and ti.done_at < ?) as production
                  from dentists d join users u on u.id = d.user_id left join appointments ap on ap.dentist_id = d.id and ap.status = 'ATENDIDA' and ap.start_at >= ? and ap.start_at < ?
                 group by d.id, u.full_name order by production desc
                """, a, b, a, b));
        List<Map<String, Object>> methods = jdbc.queryForList("""
                select method, count(*) as n, sum(amount) as amount from payments where status = 'VALIDO' and paid_at >= ? and paid_at < ?
                 group by method order by amount desc
                """, a, b);
        for (Map<String, Object> m : methods) m.put("label", METHOD.getOrDefault((String) m.get("method"), (String) m.get("method")));
        r.put("paymentMethods", methods);
        r.put("expenseCategories", jdbc.queryForList("""
                select category, sum(amount) as amount from expenses where status = 'VALIDO' and expense_date >= ? and expense_date < ?
                 group by category order by amount desc
                """, a.toLocalDate(), b.toLocalDate()));
        List<Map<String, Object>> sources = jdbc.queryForList("""
                select coalesce(source, 'SIN_DATO') as source, count(*) as n from patients where created_at >= ? and created_at < ? group by 1 order by n desc
                """, a, b);
        for (Map<String, Object> s : sources) s.put("label", pe.clinicacardenas.patient.PatientProfileService.SOURCES.stream()
                .filter(x -> x.get("code").equals(s.get("source"))).map(x -> x.get("label")).findFirst().orElse("Sin registrar"));
        r.put("sources", sources);
        return r;
    }

    private Map<String, Object> summary(int year) {
        LocalDateTime a = LocalDate.of(year, 1, 1).atStartOfDay(), b = a.plusYears(1);
        Map<String, Object> s = new LinkedHashMap<>();
        BigDecimal income = num("select coalesce(sum(amount), 0) from payments where status = 'VALIDO' and paid_at >= ? and paid_at < ?", a, b);
        BigDecimal expenses = num("select coalesce(sum(amount), 0) from expenses where status = 'VALIDO' and expense_date >= ? and expense_date < ?", a.toLocalDate(), b.toLocalDate());
        s.put("income", income);
        s.put("expenses", expenses);
        s.put("result", income.subtract(expenses));
        s.put("invoiced", num("select coalesce(sum(total_amount), 0) from invoices where status = 'ACEPTADO' and issue_date >= ? and issue_date < ?", a.toLocalDate(), b.toLocalDate()));
        s.put("newPatients", cnt("select count(*) from patients where created_at >= ? and created_at < ?", a, b));
        s.put("attendedPatients", cnt("select count(distinct patient_id) from appointments where status = 'ATENDIDA' and start_at >= ? and start_at < ?", a, b));
        long total = cnt("select count(*) from appointments where start_at >= ? and start_at < ?", a, b);
        long attended = cnt("select count(*) from appointments where status = 'ATENDIDA' and start_at >= ? and start_at < ?", a, b);
        long noShow = cnt("select count(*) from appointments where status = 'NO_ASISTIO' and start_at >= ? and start_at < ?", a, b);
        long cancelled = cnt("select count(*) from appointments where status = 'CANCELADA' and start_at >= ? and start_at < ?", a, b);
        s.put("appointments", total);
        s.put("attended", attended);
        s.put("noShow", noShow);
        s.put("cancelled", cancelled);
        s.put("noShowRate", attended + noShow == 0 ? 0 : Math.round(noShow * 1000.0 / (attended + noShow)) / 10.0);
        s.put("procedures", cnt("select count(*) from treatment_items where status = 'REALIZADO' and done_at >= ? and done_at < ?", a, b));
        s.put("averageTicket", attended == 0 ? BigDecimal.ZERO : income.divide(BigDecimal.valueOf(attended), 2, RoundingMode.HALF_UP));
        List<Map<String, Object>> sv = jdbc.queryForList("select count(*) as n, avg(score) as avg from surveys where created_at >= ? and created_at < ?", a, b);
        s.put("surveys", sv.get(0).get("n"));
        s.put("satisfaction", sv.get(0).get("avg") == null ? null : new BigDecimal(sv.get(0).get("avg").toString()).setScale(1, RoundingMode.HALF_UP));
        s.put("leads", cnt("select count(*) from leads where created_at >= ? and created_at < ?", a, b));
        s.put("leadsWon", cnt("select count(*) from leads where stage = 'GANADO' and created_at >= ? and created_at < ?", a, b));
        return s;
    }

    private List<Map<String, Object>> months(int year) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDateTime a = LocalDate.of(year, m, 1).atStartOfDay(), b = a.plusMonths(1);
            BigDecimal inc = num("select coalesce(sum(amount), 0) from payments where status = 'VALIDO' and paid_at >= ? and paid_at < ?", a, b);
            BigDecimal exp = num("select coalesce(sum(amount), 0) from expenses where status = 'VALIDO' and expense_date >= ? and expense_date < ?", a.toLocalDate(), b.toLocalDate());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", m);
            r.put("label", MES[m - 1]);
            r.put("attended", cnt("select count(*) from appointments where status = 'ATENDIDA' and start_at >= ? and start_at < ?", a, b));
            r.put("newPatients", cnt("select count(*) from patients where created_at >= ? and created_at < ?", a, b));
            r.put("income", inc);
            r.put("expenses", exp);
            r.put("result", inc.subtract(exp));
            out.add(r);
        }
        return out;
    }

    // ------------------------------------------------------------------ descargas

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public byte[] pdf(int year) {
        Map<String, Object> r = report(year);
        Map<String, Object> s = (Map<String, Object>) r.get("summary"), p = (Map<String, Object>) r.get("previous");
        SimplePdf pdf = new SimplePdf().header(clinic, address + (ruc.isBlank() ? "" : " · RUC " + ruc))
                .title("Reporte anual " + year + (Boolean.TRUE.equals(r.get("partial")) ? " (al " + Dates.today().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")" : ""))
                .paragraph("Comparado con " + (year - 1) + ". Ingresos = cobros válidos; egresos = gastos registrados en caja.", 9, false).space(6);
        List<String[]> kpi = new ArrayList<>();
        kpi.add(row("Ingresos cobrados", money(s.get("income")), money(p.get("income")), delta(s.get("income"), p.get("income"))));
        kpi.add(row("Egresos", money(s.get("expenses")), money(p.get("expenses")), delta(s.get("expenses"), p.get("expenses"))));
        kpi.add(row("Resultado", money(s.get("result")), money(p.get("result")), delta(s.get("result"), p.get("result"))));
        kpi.add(row("Comprobantes aceptados por SUNAT", money(s.get("invoiced")), money(p.get("invoiced")), delta(s.get("invoiced"), p.get("invoiced"))));
        kpi.add(row("Pacientes nuevos", str(s.get("newPatients")), str(p.get("newPatients")), delta(s.get("newPatients"), p.get("newPatients"))));
        kpi.add(row("Pacientes atendidos", str(s.get("attendedPatients")), str(p.get("attendedPatients")), delta(s.get("attendedPatients"), p.get("attendedPatients"))));
        kpi.add(row("Citas atendidas", str(s.get("attended")), str(p.get("attended")), delta(s.get("attended"), p.get("attended"))));
        kpi.add(row("Inasistencia", s.get("noShowRate") + " %", p.get("noShowRate") + " %", ""));
        kpi.add(row("Procedimientos realizados", str(s.get("procedures")), str(p.get("procedures")), delta(s.get("procedures"), p.get("procedures"))));
        kpi.add(row("Ticket promedio por cita", money(s.get("averageTicket")), money(p.get("averageTicket")), delta(s.get("averageTicket"), p.get("averageTicket"))));
        kpi.add(row("Satisfacción (1 a 5)", s.get("satisfaction") == null ? "—" : s.get("satisfaction") + " (" + s.get("surveys") + " encuestas)",
                p.get("satisfaction") == null ? "—" : String.valueOf(p.get("satisfaction")), ""));
        kpi.add(row("Leads del embudo (ganados)", s.get("leads") + " (" + s.get("leadsWon") + ")", p.get("leads") + " (" + p.get("leadsWon") + ")", ""));
        pdf.paragraph("Resumen", 12, true).table(new String[]{"Indicador", String.valueOf(year), String.valueOf(year - 1), "Variación"},
                new float[]{3.2f, 1.6f, 1.6f, 1f}, kpi, new char[]{'L', 'R', 'R', 'R'}, 9);
        List<Map<String, Object>> months = (List<Map<String, Object>>) r.get("months");
        pdf.space(6).paragraph("Ingresos cobrados por mes", 12, true)
                .bars(months.stream().map(m -> (String) m.get("label")).toArray(String[]::new),
                        months.stream().mapToDouble(m -> ((BigDecimal) m.get("income")).doubleValue()).toArray(), "0.04 0.55 0.65", "S/");
        List<String[]> mrows = new ArrayList<>();
        for (Map<String, Object> m : months) mrows.add(new String[]{(String) m.get("label"), str(m.get("attended")), str(m.get("newPatients")),
                money(m.get("income")), money(m.get("expenses")), money(m.get("result"))});
        mrows.add(new String[]{"Total", str(s.get("attended")), str(s.get("newPatients")), money(s.get("income")), money(s.get("expenses")), money(s.get("result"))});
        pdf.paragraph("Detalle mensual", 12, true).table(new String[]{"Mes", "Citas atendidas", "Pacientes nuevos", "Ingresos", "Egresos", "Resultado"},
                new float[]{0.8f, 1.2f, 1.2f, 1.4f, 1.4f, 1.4f}, mrows, new char[]{'L', 'R', 'R', 'R', 'R', 'R'}, 9);
        List<String[]> pr = new ArrayList<>();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("procedures")) pr.add(new String[]{(String) x.get("name"), str(x.get("times")), money(x.get("amount"))});
        if (!pr.isEmpty()) pdf.paragraph("Procedimientos más realizados", 12, true).table(new String[]{"Procedimiento", "Veces", "Monto"}, new float[]{4f, 1f, 1.5f}, pr, new char[]{'L', 'R', 'R'}, 9);
        List<String[]> dr = new ArrayList<>();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("dentists")) dr.add(new String[]{(String) x.get("name"), str(x.get("appointments")), money(x.get("production"))});
        if (!dr.isEmpty()) pdf.paragraph("Producción por odontólogo", 12, true).table(new String[]{"Odontólogo", "Citas atendidas", "Procedimientos realizados (S/)"}, new float[]{3f, 1.3f, 2f}, dr, new char[]{'L', 'R', 'R'}, 9);
        List<String[]> mr = new ArrayList<>();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("paymentMethods")) mr.add(new String[]{(String) x.get("label"), str(x.get("n")), money(x.get("amount"))});
        if (!mr.isEmpty()) pdf.paragraph("Cobros por medio de pago", 12, true).table(new String[]{"Medio", "Cobros", "Monto"}, new float[]{3f, 1f, 1.5f}, mr, new char[]{'L', 'R', 'R'}, 9);
        List<String[]> er = new ArrayList<>();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("expenseCategories")) er.add(new String[]{cap((String) x.get("category")), money(x.get("amount"))});
        if (!er.isEmpty()) pdf.paragraph("Egresos por categoría", 12, true).table(new String[]{"Categoría", "Monto"}, new float[]{3f, 1.5f}, er, new char[]{'L', 'R'}, 9);
        List<String[]> sr = new ArrayList<>();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("sources")) sr.add(new String[]{(String) x.get("label"), str(x.get("n"))});
        if (!sr.isEmpty()) pdf.paragraph("¿Cómo nos conocieron los pacientes nuevos?", 12, true).table(new String[]{"Fuente", "Pacientes"}, new float[]{3f, 1f}, sr, new char[]{'L', 'R'}, 9);
        pdf.space(8).paragraph("Generado el " + Dates.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " desde el sistema de la clínica.", 8, false);
        return pdf.build();
    }

    @SuppressWarnings("unchecked")
    public String csv(int year) {
        Map<String, Object> r = report(year);
        StringBuilder b = new StringBuilder("﻿");
        b.append("Reporte anual ").append(year).append(';').append(clinic).append('\n');
        b.append("Mes;Citas atendidas;Pacientes nuevos;Ingresos;Egresos;Resultado\n");
        for (Map<String, Object> m : (List<Map<String, Object>>) r.get("months"))
            b.append(m.get("label")).append(';').append(m.get("attended")).append(';').append(m.get("newPatients")).append(';')
                    .append(plain(m.get("income"))).append(';').append(plain(m.get("expenses"))).append(';').append(plain(m.get("result"))).append('\n');
        b.append("\nProcedimiento;Veces;Monto\n");
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("procedures"))
            b.append(csvText((String) x.get("name"))).append(';').append(x.get("times")).append(';').append(plain(x.get("amount"))).append('\n');
        b.append("\nOdontólogo;Citas atendidas;Producción\n");
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("dentists"))
            b.append(csvText((String) x.get("name"))).append(';').append(x.get("appointments")).append(';').append(plain(x.get("production"))).append('\n');
        b.append("\nMedio de pago;Cobros;Monto\n");
        for (Map<String, Object> x : (List<Map<String, Object>>) r.get("paymentMethods"))
            b.append(x.get("label")).append(';').append(x.get("n")).append(';').append(plain(x.get("amount"))).append('\n');
        return b.toString();
    }

    // ------------------------------------------------------------------ utilidades

    private BigDecimal num(String sql, Object... a) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, a); return v == null ? BigDecimal.ZERO : v; }
    private long cnt(String sql, Object... a) { Long v = jdbc.queryForObject(sql, Long.class, a); return v == null ? 0 : v; }
    private static String[] row(String... c) { return c; }
    private static String str(Object o) { return o == null ? "0" : String.valueOf(o); }
    private static String plain(Object o) { return o == null ? "0" : new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','); }
    private static String csvText(String s) { return s == null ? "" : s.replace(";", ","); }
    private static String cap(String s) { return s == null ? "" : s.charAt(0) + s.substring(1).toLowerCase().replace('_', ' '); }

    static String money(Object o) {
        BigDecimal v = o == null ? BigDecimal.ZERO : new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat f = new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(Locale.US));
        return (v.signum() < 0 ? "-S/ " : "S/ ") + f.format(v.abs());
    }

    private static String delta(Object cur, Object prev) {
        double c = cur == null ? 0 : Double.parseDouble(cur.toString()), p = prev == null ? 0 : Double.parseDouble(prev.toString());
        if (p == 0) return c == 0 ? "—" : "nuevo";
        double d = (c - p) * 100 / Math.abs(p);
        return (d >= 0 ? "+" : "") + String.format(Locale.US, "%.0f", d) + " %";
    }
}
