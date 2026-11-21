package pe.clinicacardenas.crm;

import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.patient.PatientProfileService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * v2.7: filtros de pacientes. Los usan la lista de pacientes, los segmentos guardados y las campañas.
 * Todos los campos son opcionales; los que vienen se combinan con Y.
 *
 * visit: NUNCA (sin citas atendidas), SIN_3M / SIN_6M / SIN_12M (no viene hace N meses), CON_3M (vino en los últimos 3 meses).
 * nextAppointment / task: CON | SIN. budget: PENDIENTE (tiene procedimientos por hacer en un plan activo).
 * marketing: SI | NO | SIN_RESPUESTA. registered: 30D | 90D | 365D.
 */
public record SegmentFilter(List<String> sources, List<String> tags, String sex, Integer ageMin, Integer ageMax,
                            Integer birthdayMonth, String visit, String nextAppointment, String budget, Boolean debt,
                            String task, String marketing, String registered, Long dentistId) {

    public static final Set<String> VISITS = Set.of("NUNCA", "SIN_3M", "SIN_6M", "SIN_12M", "CON_3M");

    public static SegmentFilter empty() {
        return new SegmentFilter(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Normaliza y valida. Lanza 400 con el campo que está mal. */
    public SegmentFilter clean() {
        Map<String, String> e = new LinkedHashMap<>();
        List<String> src = up(sources);
        for (String s : src) if (PatientProfileService.SOURCES.stream().noneMatch(x -> x.get("code").equals(s))) e.put("sources", "Fuente no válida: " + s);
        List<String> tg = tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toList();
        String sx = blank(sex) ? null : sex.trim().toUpperCase();
        if (sx != null && !Set.of("F", "M").contains(sx)) e.put("sex", "Elige femenino o masculino.");
        if (ageMin != null && (ageMin < 0 || ageMin > 120)) e.put("ageMin", "Edad entre 0 y 120.");
        if (ageMax != null && (ageMax < 0 || ageMax > 120)) e.put("ageMax", "Edad entre 0 y 120.");
        if (ageMin != null && ageMax != null && ageMin > ageMax) e.put("ageMax", "La edad máxima no puede ser menor que la mínima.");
        if (birthdayMonth != null && (birthdayMonth < 1 || birthdayMonth > 12)) e.put("birthdayMonth", "Mes no válido.");
        String v = code(visit);
        if (v != null && !VISITS.contains(v)) e.put("visit", "Opción no válida.");
        String na = code(nextAppointment), tk = code(task), bu = code(budget), mk = code(marketing), rg = code(registered);
        if (na != null && !Set.of("CON", "SIN").contains(na)) e.put("nextAppointment", "Opción no válida.");
        if (tk != null && !Set.of("CON", "SIN").contains(tk)) e.put("task", "Opción no válida.");
        if (bu != null && !"PENDIENTE".equals(bu)) e.put("budget", "Opción no válida.");
        if (mk != null && !Set.of("SI", "NO", "SIN_RESPUESTA").contains(mk)) e.put("marketing", "Opción no válida.");
        if (rg != null && !Set.of("30D", "90D", "365D").contains(rg)) e.put("registered", "Opción no válida.");
        if (!e.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los filtros.", e);
        return new SegmentFilter(src.isEmpty() ? null : src, tg.isEmpty() ? null : tg, sx, ageMin, ageMax, birthdayMonth, v, na, bu,
                Boolean.TRUE.equals(debt) ? Boolean.TRUE : null, tk, mk, rg, dentistId);
    }

    public boolean isEmpty() {
        return sources == null && tags == null && sex == null && ageMin == null && ageMax == null && birthdayMonth == null && visit == null
                && nextAppointment == null && budget == null && debt == null && task == null && marketing == null && registered == null && dentistId == null;
    }

    /** Condiciones SQL sobre el alias {@code p} (tabla patients). */
    public record Where(String sql, List<Object> args) {}

    public Where where(LocalDateTime now) {
        List<String> c = new ArrayList<>();
        List<Object> a = new ArrayList<>();
        c.add("p.active = true");
        LocalDate today = now.toLocalDate();
        if (sources != null) { c.add("p.source in (" + marks(sources.size()) + ")"); a.addAll(sources); }
        if (tags != null) { c.add("exists (select 1 from patient_tags t where t.patient_id = p.id and t.tag in (" + marks(tags.size()) + "))"); a.addAll(tags); }
        if (sex != null) { c.add("p.sex = ?"); a.add(sex); }
        // edad cumplida: nacido en o antes de hoy - N años
        if (ageMin != null) { c.add("p.birth_date <= ?"); a.add(today.minusYears(ageMin)); }
        if (ageMax != null) { c.add("p.birth_date > ?"); a.add(today.minusYears(ageMax + 1L)); }
        if (birthdayMonth != null) { c.add("month(p.birth_date) = ?"); a.add(birthdayMonth); }
        String lastVisit = "(select max(av.start_at) from appointments av where av.patient_id = p.id and av.status = 'ATENDIDA')";
        if (visit != null) {
            switch (visit) {
                case "NUNCA" -> c.add(lastVisit + " is null");
                case "CON_3M" -> { c.add(lastVisit + " >= ?"); a.add(now.minusMonths(3)); }
                default -> {
                    int m = Integer.parseInt(visit.replaceAll("\\D", ""));
                    c.add(lastVisit + " < ?"); a.add(now.minusMonths(m));
                }
            }
        }
        if (nextAppointment != null) {
            c.add(("SIN".equals(nextAppointment) ? "not " : "") + "exists (select 1 from appointments an where an.patient_id = p.id and an.start_at > ? "
                    + "and an.status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION'))");
            a.add(now);
        }
        if (budget != null) c.add("exists (select 1 from treatment_items bi join treatment_plans bp on bp.id = bi.plan_id "
                + "where bp.patient_id = p.id and bp.status = 'ACTIVO' and bi.status in ('PENDIENTE', 'EN_PROCESO'))");
        if (debt != null) c.add("(coalesce((select sum(di.price) from treatment_items di join treatment_plans dp on dp.id = di.plan_id "
                + "where dp.patient_id = p.id and di.status = 'REALIZADO'), 0) - coalesce((select sum(py.amount) from payments py "
                + "where py.patient_id = p.id and py.status = 'VALIDO'), 0)) > 0.009");
        if (task != null) c.add(("SIN".equals(task) ? "not " : "") + "exists (select 1 from tasks tk where tk.patient_id = p.id and tk.status = 'PENDIENTE')");
        if (marketing != null) c.add(switch (marketing) {
            case "SI" -> "p.marketing_consent = true";
            case "NO" -> "p.marketing_consent = false";
            default -> "p.marketing_consent is null";
        });
        if (registered != null) { c.add("p.created_at >= ?"); a.add(now.minusDays(Integer.parseInt(registered.replaceAll("\\D", "")))); }
        if (dentistId != null) { c.add("exists (select 1 from appointments ad where ad.patient_id = p.id and ad.dentist_id = ? and ad.status = 'ATENDIDA')"); a.add(dentistId); }
        return new Where(String.join(" and ", c), a);
    }

    /** Resumen legible, p. ej. "Instagram · sin visita hace 6 meses · aceptan publicidad". */
    public String describe() {
        List<String> s = new ArrayList<>();
        if (sources != null) s.add("Fuente: " + String.join(", ", sources.stream().map(x -> PatientProfileService.SOURCES.stream()
                .filter(o -> o.get("code").equals(x)).map(o -> o.get("label")).findFirst().orElse(x)).toList()));
        if (tags != null) s.add("Etiqueta: " + String.join(", ", tags));
        if (sex != null) s.add("F".equals(sex) ? "Mujeres" : "Hombres");
        if (ageMin != null || ageMax != null) s.add(ageMin != null && ageMax != null ? ageMin + " a " + ageMax + " años"
                : ageMin != null ? "Desde " + ageMin + " años" : "Hasta " + ageMax + " años");
        if (birthdayMonth != null) s.add("Cumpleaños en " + MONTHS[birthdayMonth - 1]);
        if (visit != null) s.add(switch (visit) {
            case "NUNCA" -> "Nunca atendidos";
            case "CON_3M" -> "Vinieron en los últimos 3 meses";
            default -> "Sin visita hace " + visit.replaceAll("\\D", "") + " meses";
        });
        if (nextAppointment != null) s.add("CON".equals(nextAppointment) ? "Con próxima cita" : "Sin próxima cita");
        if (budget != null) s.add("Con presupuesto pendiente");
        if (debt != null) s.add("Con saldo por pagar");
        if (task != null) s.add("CON".equals(task) ? "Con tarea pendiente" : "Sin tarea pendiente");
        if (marketing != null) s.add(switch (marketing) {
            case "SI" -> "Aceptan publicidad";
            case "NO" -> "No aceptan publicidad";
            default -> "Sin respuesta de publicidad";
        });
        if (registered != null) s.add("Registrados en los últimos " + registered.replaceAll("\\D", "") + " días");
        if (dentistId != null) s.add("Atendidos por el odontólogo #" + dentistId);
        return s.isEmpty() ? "Todos los pacientes activos" : String.join(" · ", s);
    }

    public static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
            "octubre", "noviembre", "diciembre"};

    private static String marks(int n) { return String.join(", ", Collections.nCopies(n, "?")); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String code(String s) { return blank(s) ? null : s.trim().toUpperCase(); }
    private static List<String> up(List<String> l) {
        return l == null ? List.of() : l.stream().filter(x -> x != null && !x.isBlank()).map(x -> x.trim().toUpperCase()).distinct().toList();
    }
}
