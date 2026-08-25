package pe.clinica.cardenas.financeservice.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.financeservice.domain.DoctorCommissionRule;
import pe.clinica.cardenas.financeservice.domain.Payment;
import pe.clinica.cardenas.financeservice.repository.DoctorCommissionRuleRepository;
import pe.clinica.cardenas.financeservice.repository.PaymentRepository;
import pe.clinica.cardenas.financeservice.service.EncounterClient;
import pe.clinica.cardenas.financeservice.service.LabClient;
import pe.clinica.cardenas.financeservice.service.CommissionCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinancialAnalyticsController {
    private final PaymentRepository payments;
    private final DoctorCommissionRuleRepository commissionRules;
    private final EncounterClient encounterClient;
    private final LabClient labClient;
    private final CommissionCalculationService commissionCalculationService;

    @GetMapping("/reports/daily")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String,Object> daily(@RequestParam(required=false) LocalDate date, HttpServletRequest request) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return incomeReport(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), "DIA", day.toString(), request.getHeader("Authorization"));
    }

    @GetMapping("/reports/monthly")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String,Object> monthly(@RequestParam(required=false) String month, HttpServletRequest request) {
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return incomeReport(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay(), "MES", ym.toString(), request.getHeader("Authorization"));
    }

    @GetMapping("/commission-preview")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String,Object> commissionPreview(@RequestParam(required=false) String month, HttpServletRequest request) {
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        String authorization = request.getHeader("Authorization");
        List<Map<String,Object>> rows = new ArrayList<>();
        for (DoctorCommissionRule rule : commissionRules.findAllByOrderByDoctorNameAsc()) {
            if (!rule.isActive()) continue;
            CommissionCalculationService.Result r = commissionCalculationService.calculate(rule.getDoctorName(), ym, authorization);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("doctorName", r.doctorName()); m.put("agreedAmount", r.agreedAmount()); m.put("laboratoryCosts", r.laboratoryCosts());
            m.put("commissionBase", r.commissionBase()); m.put("commissionRate", r.effectiveRate()); m.put("defaultRate", r.defaultRate());
            m.put("commissionAmount", r.commissionAmount()); m.put("proformaCount", r.proformaCount()); m.put("labOrderCount", r.labOrderCount());
            m.put("configured", r.configured()); m.put("treatments", r.treatments());
            rows.add(m);
        }
        rows.sort(Comparator.comparing(x -> text(x.get("doctorName"))));
        BigDecimal agreedTotal = rows.stream().map(x -> decimal(x.get("agreedAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal labTotal = rows.stream().map(x -> decimal(x.get("laboratoryCosts"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baseTotal = rows.stream().map(x -> decimal(x.get("commissionBase"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commissionTotal = rows.stream().map(x -> decimal(x.get("commissionAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString()); out.put("agreedTotal", agreedTotal); out.put("laboratoryTotal", labTotal);
        out.put("commissionBaseTotal", baseTotal); out.put("commissionTotal", commissionTotal); out.put("rows", rows);
        out.put("calculationRule", "Porcentaje por tratamiento si existe excepción; en caso contrario usa el porcentaje predeterminado del doctor. El laboratorio se prorratea proporcionalmente antes de calcular cada comisión.");
        return out;
    }

    private Map<String,Object> incomeReport(LocalDateTime from, LocalDateTime to, String type, String period, String auth) {
        List<Payment> allValid = payments.findAll().stream()
                .filter(p -> !"ANULADO".equalsIgnoreCase(p.getPaymentStatus()))
                .filter(p -> p.getPaidAt() != null)
                .sorted(Comparator.comparing(Payment::getPaidAt))
                .toList();

        // Fecha de primera relación clínica. Si un paciente aún no tiene Atención Clínica,
        // se usa su primer ingreso como respaldo para no perder la clasificación.
        Map<Long,LocalDate> firstHistoryDate = new HashMap<>();
        for (Map<String,Object> e : encounterClient.encounters(auth)) {
            Long patientId = longOrNull(e.get("patientId"));
            LocalDateTime scheduled = dateTime(e.get("scheduledStart"));
            if (patientId != null && scheduled != null)
                firstHistoryDate.merge(patientId, scheduled.toLocalDate(), (a,b) -> a.isBefore(b) ? a : b);
        }
        for (Payment p : allValid) if (p.getPatientId() != null)
            firstHistoryDate.merge(p.getPatientId(), p.getPaidAt().toLocalDate(), (a,b) -> a.isBefore(b) ? a : b);

        List<Payment> periodPayments = allValid.stream()
                .filter(p -> !p.getPaidAt().isBefore(from) && p.getPaidAt().isBefore(to))
                .toList();

        LocalDate periodStart = from.toLocalDate();
        LocalDate periodEndExclusive = to.toLocalDate();
        BigDecimal total = BigDecimal.ZERO, newIncome = BigDecimal.ZERO, continuingIncome = BigDecimal.ZERO;
        Set<Long> newPatients = new HashSet<>(), continuingPatients = new HashSet<>();
        Map<String,MethodAccumulator> methods = new LinkedHashMap<>();
        Map<LocalDate,BigDecimal> daily = new TreeMap<>();
        List<Map<String,Object>> rows = new ArrayList<>();

        for (Payment p : periodPayments) {
            BigDecimal amount = nvl(p.getAmount()); total = total.add(amount);
            LocalDate first = p.getPatientId() == null ? null : firstHistoryDate.get(p.getPatientId());
            boolean isNew = first != null && !first.isBefore(periodStart) && first.isBefore(periodEndExclusive);
            String patientType = isNew ? "NUEVO" : "CONTINUO";
            if (isNew) { newIncome = newIncome.add(amount); if(p.getPatientId()!=null)newPatients.add(p.getPatientId()); }
            else { continuingIncome = continuingIncome.add(amount); if (p.getPatientId()!=null) continuingPatients.add(p.getPatientId()); }
            String method = text(p.getPaymentMethod()).isBlank() ? "OTRO" : text(p.getPaymentMethod()).toUpperCase(Locale.ROOT);
            MethodAccumulator ma = methods.computeIfAbsent(method, k -> new MethodAccumulator()); ma.total = ma.total.add(amount); ma.count++;
            daily.merge(p.getPaidAt().toLocalDate(), amount, BigDecimal::add);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("paidAt", p.getPaidAt()); row.put("patientId", p.getPatientId()); row.put("patientName", p.getPatientName());
            row.put("patientType", patientType); row.put("firstHistoryDate", first); row.put("concept", p.getConcept()); row.put("amount", amount); row.put("paymentMethod", method);
            row.put("reference", p.getReference()); row.put("registeredBy", p.getRegisteredBy());
            rows.add(row);
        }

        List<Map<String,Object>> methodRows = methods.entrySet().stream().map(e -> Map.<String,Object>of("name",e.getKey(),"total",e.getValue().total,"count",e.getValue().count)).toList();
        List<Map<String,Object>> dailyRows = daily.entrySet().stream().map(e -> Map.<String,Object>of("date",e.getKey(),"total",e.getValue())).toList();
        Set<Long> unique = periodPayments.stream().map(Payment::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("type", type); out.put("period", period); out.put("from", from); out.put("toExclusive", to);
        out.put("totalIncome", total); out.put("paymentCount", periodPayments.size()); out.put("uniquePatients", unique.size());
        out.put("newPatientCount", newPatients.size()); out.put("continuingPatientCount", continuingPatients.size());
        out.put("newPatientIncome", newIncome); out.put("continuingPatientIncome", continuingIncome);
        out.put("paymentMethods", methodRows); out.put("dailyTotals", dailyRows); out.put("rows", rows);
        out.put("classificationRule", "NUEVO = su primera atención registrada pertenece al periodo consultado; CONTINUO = ya tenía historial anterior. Si no existe atención, se usa el primer ingreso registrado como respaldo.");
        return out;
    }

    private static class MethodAccumulator { BigDecimal total = BigDecimal.ZERO; long count; }
    private static class CommissionAccumulator {
        final String doctorName; BigDecimal rate; BigDecimal agreed = BigDecimal.ZERO; BigDecimal lab = BigDecimal.ZERO; long proformaCount; long labCount;
        CommissionAccumulator(String doctorName, BigDecimal rate) { this.doctorName = doctorName; this.rate = rate == null ? BigDecimal.ZERO : rate; }
    }
    private BigDecimal nvl(BigDecimal v){ return v == null ? BigDecimal.ZERO : v; }
    private BigDecimal decimal(Object v){ if(v == null) return BigDecimal.ZERO; try { return new BigDecimal(String.valueOf(v)); } catch(Exception e) { return BigDecimal.ZERO; } }
    private String text(Object v){ return v == null ? "" : String.valueOf(v); }
    private String norm(String v){ return v == null ? "" : v.trim().toLowerCase(Locale.ROOT); }
    private LocalDateTime dateTime(Object v){ if(v==null || String.valueOf(v).isBlank()) return null; try{return LocalDateTime.parse(String.valueOf(v));}catch(Exception e){return null;} }
    private LocalDate localDate(Object v){ if(v==null || String.valueOf(v).isBlank()) return null; try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){return null;} }
    private Long longOrNull(Object v){ if(v==null || String.valueOf(v).isBlank()) return null; try{return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}catch(Exception e){return null;} }
}
