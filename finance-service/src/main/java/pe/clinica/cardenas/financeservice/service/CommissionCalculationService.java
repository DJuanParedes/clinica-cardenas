package pe.clinica.cardenas.financeservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.clinica.cardenas.financeservice.domain.DoctorCommissionRule;
import pe.clinica.cardenas.financeservice.domain.DoctorTreatmentCommissionRule;
import pe.clinica.cardenas.financeservice.repository.DoctorCommissionRuleRepository;
import pe.clinica.cardenas.financeservice.repository.DoctorTreatmentCommissionRuleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommissionCalculationService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final DoctorCommissionRuleRepository defaultRules;
    private final DoctorTreatmentCommissionRuleRepository treatmentRules;
    private final EncounterClient encounterClient;
    private final LabClient labClient;

    public Result calculate(String doctorName, YearMonth month, String authorization) {
        DoctorCommissionRule defaultRule = defaultRules.findByDoctorNameIgnoreCase(doctorName)
                .filter(DoctorCommissionRule::isActive).orElse(null);
        BigDecimal defaultRate = defaultRule == null ? BigDecimal.ZERO : nvl(defaultRule.getCommissionRate());
        Map<String, DoctorTreatmentCommissionRule> overrides = treatmentRules
                .findAllByDoctorNameIgnoreCaseOrderByTreatmentNameAsc(doctorName).stream()
                .filter(DoctorTreatmentCommissionRule::isActive)
                .collect(Collectors.toMap(x -> norm(x.getTreatmentName()), x -> x, (a,b) -> a, LinkedHashMap::new));

        List<RawLine> raw = new ArrayList<>();
        long proformaCount = 0;
        for (Map<String,Object> p : encounterClient.proformas(authorization)) {
            if (!doctorName.equalsIgnoreCase(text(p.get("doctorName")))) continue;
            String status = text(p.get("status")).toUpperCase(Locale.ROOT);
            if (!Set.of("ACEPTADA","EN_TRATAMIENTO","COMPLETADA").contains(status)) continue;
            LocalDateTime effective = firstDateTime(p.get("acceptedAt"), p.get("updatedAt"), p.get("createdAt"));
            if (effective == null || !YearMonth.from(effective).equals(month)) continue;
            BigDecimal total = decimal(p.get("totalAmount"));
            List<Map<String,Object>> lines = mapList(p.get("lines"));
            if (lines.isEmpty()) {
                raw.add(new RawLine("Plan general", total, defaultRate, false));
            } else {
                BigDecimal subtotal = lines.stream().map(this::lineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal assigned = BigDecimal.ZERO;
                for (int i=0;i<lines.size();i++) {
                    Map<String,Object> line = lines.get(i);
                    String treatment = text(line.get("treatmentName")).isBlank() ? "Tratamiento sin nombre" : text(line.get("treatmentName"));
                    BigDecimal accepted;
                    if (i == lines.size()-1) accepted = total.subtract(assigned).max(BigDecimal.ZERO);
                    else if (subtotal.signum() > 0) accepted = total.multiply(lineSubtotal(line)).divide(subtotal, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                    else accepted = total.divide(BigDecimal.valueOf(lines.size()), 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                    assigned = assigned.add(accepted);
                    DoctorTreatmentCommissionRule override = overrides.get(norm(treatment));
                    BigDecimal rate = override == null ? defaultRate : nvl(override.getCommissionRate());
                    raw.add(new RawLine(treatment, accepted, rate, override != null));
                }
            }
            proformaCount++;
        }

        BigDecimal labTotal = BigDecimal.ZERO;
        long labCount = 0;
        for (Map<String,Object> l : labClient.orders(authorization)) {
            if (!doctorName.equalsIgnoreCase(text(l.get("doctorName")))) continue;
            if (text(l.get("labStatus")).toUpperCase(Locale.ROOT).contains("CANCEL")) continue;
            LocalDate effective = firstDate(l.get("receivedDate"), l.get("sentDate"), l.get("expectedDate"));
            if (effective != null && YearMonth.from(effective).equals(month)) {
                labTotal = labTotal.add(decimal(l.get("cost")));
                labCount++;
            }
        }

        BigDecimal agreed = raw.stream().map(RawLine::accepted).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal labToAllocate = labTotal.min(agreed);
        List<CalculatedLine> calculated = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i=0;i<raw.size();i++) {
            RawLine line = raw.get(i);
            BigDecimal labShare;
            if (i == raw.size()-1) labShare = labToAllocate.subtract(allocated).max(BigDecimal.ZERO);
            else if (agreed.signum() > 0) labShare = labToAllocate.multiply(line.accepted()).divide(agreed, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            else labShare = BigDecimal.ZERO;
            allocated = allocated.add(labShare);
            BigDecimal base = line.accepted().subtract(labShare).max(BigDecimal.ZERO);
            BigDecimal commission = base.multiply(line.rate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            calculated.add(new CalculatedLine(line.treatmentName(), line.accepted(), labShare, base, line.rate(), commission, line.overrideApplied()));
        }

        Map<String, TreatmentBreakdownMutable> grouped = new LinkedHashMap<>();
        for (CalculatedLine line : calculated) {
            String key = norm(line.treatmentName());
            TreatmentBreakdownMutable g = grouped.computeIfAbsent(key, k -> new TreatmentBreakdownMutable(line.treatmentName(), line.rate(), line.overrideApplied()));
            g.accepted = g.accepted.add(line.accepted()); g.lab = g.lab.add(line.labShare()); g.base = g.base.add(line.base()); g.commission = g.commission.add(line.commission());
        }
        List<TreatmentBreakdown> breakdown = grouped.values().stream()
                .map(g -> new TreatmentBreakdown(g.treatmentName, g.accepted, g.lab, g.base, g.rate, g.commission, g.overrideApplied))
                .sorted(Comparator.comparing(TreatmentBreakdown::treatmentName, String.CASE_INSENSITIVE_ORDER)).toList();
        BigDecimal baseTotal = calculated.stream().map(CalculatedLine::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commissionTotal = calculated.stream().map(CalculatedLine::commission).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effectiveRate = baseTotal.signum() == 0 ? BigDecimal.ZERO : commissionTotal.multiply(HUNDRED).divide(baseTotal, 2, RoundingMode.HALF_UP);
        return new Result(doctorName, month.toString(), defaultRule != null, defaultRate, agreed, labTotal, baseTotal, commissionTotal,
                effectiveRate, proformaCount, labCount, breakdown);
    }

    private BigDecimal lineSubtotal(Map<String,Object> line) {
        Object direct = line.get("subtotal");
        if (direct != null) return decimal(direct);
        return decimal(line.get("unitPrice")).multiply(BigDecimal.valueOf(longValue(line.get("quantity"),1)));
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object x : list) if (x instanceof Map<?,?> m) {
            Map<String,Object> copy = new LinkedHashMap<>(); m.forEach((k,v)->copy.put(String.valueOf(k),v)); out.add(copy);
        }
        return out;
    }
    private LocalDateTime firstDateTime(Object... values) { for(Object v:values){LocalDateTime d=dateTime(v);if(d!=null)return d;}return null; }
    private LocalDate firstDate(Object... values) { for(Object v:values){LocalDate d=localDate(v);if(d!=null)return d;}return null; }
    private BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private BigDecimal decimal(Object v){if(v==null)return BigDecimal.ZERO;try{return new BigDecimal(String.valueOf(v));}catch(Exception e){return BigDecimal.ZERO;}}
    private long longValue(Object v,long fallback){try{return v instanceof Number n?n.longValue():Long.parseLong(String.valueOf(v));}catch(Exception e){return fallback;}}
    private String text(Object v){return v==null?"":String.valueOf(v);}
    private String norm(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
    private LocalDateTime dateTime(Object v){if(v==null||String.valueOf(v).isBlank())return null;try{return LocalDateTime.parse(String.valueOf(v));}catch(Exception e){return null;}}
    private LocalDate localDate(Object v){if(v==null||String.valueOf(v).isBlank())return null;try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){return null;}}

    private record RawLine(String treatmentName, BigDecimal accepted, BigDecimal rate, boolean overrideApplied) {}
    private record CalculatedLine(String treatmentName, BigDecimal accepted, BigDecimal labShare, BigDecimal base, BigDecimal rate, BigDecimal commission, boolean overrideApplied) {}
    private static class TreatmentBreakdownMutable { final String treatmentName; final BigDecimal rate; final boolean overrideApplied; BigDecimal accepted=BigDecimal.ZERO,lab=BigDecimal.ZERO,base=BigDecimal.ZERO,commission=BigDecimal.ZERO; TreatmentBreakdownMutable(String n,BigDecimal r,boolean o){treatmentName=n;rate=r;overrideApplied=o;} }
    public record TreatmentBreakdown(String treatmentName, BigDecimal acceptedAmount, BigDecimal laboratoryShare, BigDecimal commissionBase, BigDecimal commissionRate, BigDecimal commissionAmount, boolean overrideApplied) {}
    public record Result(String doctorName, String period, boolean configured, BigDecimal defaultRate, BigDecimal agreedAmount,
                         BigDecimal laboratoryCosts, BigDecimal commissionBase, BigDecimal commissionAmount, BigDecimal effectiveRate,
                         long proformaCount, long labOrderCount, List<TreatmentBreakdown> treatments) {}
}
