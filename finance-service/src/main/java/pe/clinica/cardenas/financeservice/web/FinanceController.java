package pe.clinica.cardenas.financeservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.financeservice.domain.DoctorCommission;
import pe.clinica.cardenas.financeservice.domain.DoctorCommissionRule;
import pe.clinica.cardenas.financeservice.domain.DoctorTreatmentCommissionRule;
import pe.clinica.cardenas.financeservice.domain.Membership;
import pe.clinica.cardenas.financeservice.repository.DoctorCommissionRepository;
import pe.clinica.cardenas.financeservice.repository.DoctorCommissionRuleRepository;
import pe.clinica.cardenas.financeservice.repository.DoctorTreatmentCommissionRuleRepository;
import pe.clinica.cardenas.financeservice.repository.MembershipRepository;
import pe.clinica.cardenas.financeservice.service.SchedulingClient;
import pe.clinica.cardenas.financeservice.service.EncounterClient;
import pe.clinica.cardenas.financeservice.service.LabClient;
import pe.clinica.cardenas.financeservice.service.CommissionCalculationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {
    private static final Set<String> MEMBERSHIP_STATUSES = Set.of("ACTIVA","SUSPENDIDA","VENCIDA","CANCELADA","COMPLETADA");
    private final DoctorCommissionRepository commissions;
    private final MembershipRepository memberships;
    private final DoctorCommissionRuleRepository commissionRules;
    private final DoctorTreatmentCommissionRuleRepository treatmentCommissionRules;
    private final CommissionCalculationService commissionCalculationService;
    private final SchedulingClient schedulingClient;
    private final EncounterClient encounterClient;
    private final LabClient labClient;

    @GetMapping("/commission-rules")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public List<DoctorCommissionRule> commissionRules() { return commissionRules.findAllByOrderByDoctorNameAsc(); }

    @PostMapping("/commission-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorCommissionRule saveCommissionRule(@RequestBody DoctorCommissionRule incoming, org.springframework.security.core.Authentication auth, jakarta.servlet.http.HttpServletRequest request) {
        if (incoming.getDoctorName() == null || incoming.getDoctorName().isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Selecciona un doctor");
        String doctorName = incoming.getDoctorName().trim();
        boolean doctorExists = schedulingClient.doctors(request.getHeader("Authorization")).stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.get("active")) && doctorName.equalsIgnoreCase(String.valueOf(d.get("fullName"))));
        if (!doctorExists) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "El doctor no existe o está inactivo en Agenda");
        BigDecimal rate = incoming.getCommissionRate() == null ? BigDecimal.ZERO : incoming.getCommissionRate();
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "La comisión debe estar entre 0% y 100%");
        DoctorCommissionRule rule = commissionRules.findByDoctorNameIgnoreCase(doctorName).orElseGet(DoctorCommissionRule::new);
        rule.setDoctorName(doctorName);
        rule.setCommissionRate(rate);
        rule.setActive(incoming.isActive());
        rule.setUpdatedBy(auth.getName());
        rule.setUpdatedAt(LocalDateTime.now());
        return commissionRules.save(rule);
    }

    @GetMapping("/commission-treatment-rules")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public List<DoctorTreatmentCommissionRule> treatmentCommissionRules() {
        return treatmentCommissionRules.findAllByOrderByDoctorNameAscTreatmentNameAsc();
    }

    @PostMapping("/commission-treatment-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorTreatmentCommissionRule saveTreatmentCommissionRule(@RequestBody DoctorTreatmentCommissionRule incoming,
                                                                      org.springframework.security.core.Authentication auth,
                                                                      jakarta.servlet.http.HttpServletRequest request) {
        String doctorName = incoming.getDoctorName() == null ? "" : incoming.getDoctorName().trim();
        String treatmentName = incoming.getTreatmentName() == null ? "" : incoming.getTreatmentName().trim();
        if (doctorName.isBlank() || treatmentName.isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Selecciona doctor y tratamiento");
        String authorization = request.getHeader("Authorization");
        boolean doctorExists = schedulingClient.doctors(authorization).stream().anyMatch(d -> Boolean.TRUE.equals(d.get("active")) && doctorName.equalsIgnoreCase(String.valueOf(d.get("fullName"))));
        if (!doctorExists) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "El doctor no existe o está inactivo");
        boolean treatmentExists = encounterClient.treatments(authorization).stream().anyMatch(t -> Boolean.TRUE.equals(t.get("active")) && treatmentName.equalsIgnoreCase(String.valueOf(t.get("name"))));
        if (!treatmentExists) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "El tratamiento no existe o está inactivo");
        BigDecimal rate = incoming.getCommissionRate() == null ? BigDecimal.ZERO : incoming.getCommissionRate();
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "La comisión debe estar entre 0% y 100%");
        DoctorTreatmentCommissionRule rule = treatmentCommissionRules.findByDoctorNameIgnoreCaseAndTreatmentNameIgnoreCase(doctorName, treatmentName).orElseGet(DoctorTreatmentCommissionRule::new);
        rule.setDoctorName(doctorName); rule.setTreatmentName(treatmentName); rule.setCommissionRate(rate); rule.setActive(incoming.isActive());
        rule.setUpdatedBy(auth.getName()); rule.setUpdatedAt(LocalDateTime.now());
        return treatmentCommissionRules.save(rule);
    }

    @DeleteMapping("/commission-treatment-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteTreatmentCommissionRule(@PathVariable Long id) {
        DoctorTreatmentCommissionRule rule = treatmentCommissionRules.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Regla no encontrada"));
        treatmentCommissionRules.delete(rule);
    }

    @GetMapping("/commissions")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS','DOCTOR')")
    public List<DoctorCommission> commissions(org.springframework.security.core.Authentication auth) {
        List<DoctorCommission> all = commissions.findAllByOrderByDoctorNameAsc();
        boolean doctor = auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_DOCTOR"));
        if (!doctor) return all;
        String fullName = auth.getDetails() == null ? "" : auth.getDetails().toString();
        return all.stream().filter(x -> x.getDoctorName() != null && x.getDoctorName().equalsIgnoreCase(fullName)).toList();
    }

    @PostMapping("/commissions/generate")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public DoctorCommission generateCommission(@RequestParam String month, @RequestParam String doctorName, jakarta.servlet.http.HttpServletRequest request) {
        YearMonth ym;
        try { ym = YearMonth.parse(month); } catch (Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Periodo inválido; usa YYYY-MM"); }
        String cleanDoctor = doctorName == null ? "" : doctorName.trim();
        if (cleanDoctor.isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Selecciona un doctor");
        CommissionCalculationService.Result result = commissionCalculationService.calculate(cleanDoctor, ym, request.getHeader("Authorization"));
        if (!result.configured()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "El Administrador debe configurar primero el porcentaje predeterminado de este doctor");
        DoctorCommission item = commissions.findFirstByDoctorNameIgnoreCaseAndPeriod(cleanDoctor, ym.toString()).orElseGet(DoctorCommission::new);
        if ("PAGADO".equalsIgnoreCase(item.getPaymentStatus())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "La liquidación ya fue pagada y no puede recalcularse");
        item.setDoctorName(cleanDoctor); item.setPeriod(ym.toString()); item.setGrossProduction(result.agreedAmount()); item.setCollectedProduction(BigDecimal.ZERO);
        item.setCommissionRate(result.effectiveRate()); item.setLaboratoryCosts(result.laboratoryCosts()); item.setFinalAmount(result.commissionAmount());
        if (item.getPaymentStatus() == null || item.getPaymentStatus().isBlank()) item.setPaymentStatus("CALCULADO");
        return commissions.save(item);
    }

    @PatchMapping("/commissions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public DoctorCommission commissionStatus(@PathVariable Long id, @RequestParam String value) {
        DoctorCommission commission = commissions.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Liquidación no encontrada"));
        String current = commission.getPaymentStatus() == null ? "CALCULADO" : commission.getPaymentStatus().trim().toUpperCase(Locale.ROOT);
        String next = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of("CALCULADO", "EN REVISIÓN", "APROBADO", "PAGADO", "OBSERVADO");
        if (!allowed.contains(next)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Estado de liquidación no permitido");
        if ("PAGADO".equals(current) && !"PAGADO".equals(next)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Una liquidación pagada es definitiva y no puede reabrirse");
        Map<String,Set<String>> transitions = Map.of(
                "CALCULADO", Set.of("CALCULADO","EN REVISIÓN","APROBADO","OBSERVADO"),
                "EN REVISIÓN", Set.of("EN REVISIÓN","APROBADO","OBSERVADO"),
                "OBSERVADO", Set.of("OBSERVADO","EN REVISIÓN","APROBADO"),
                "APROBADO", Set.of("APROBADO","PAGADO","OBSERVADO"),
                "PAGADO", Set.of("PAGADO")
        );
        if (!transitions.getOrDefault(current, Set.of()).contains(next)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Transición de liquidación no permitida: " + current + " → " + next);
        commission.setPaymentStatus(next);
        return commissions.save(commission);
    }

    @GetMapping("/memberships")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public List<Membership> memberships() { return memberships.findAllByOrderByEndDateAsc(); }

    @PostMapping("/memberships")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Membership createMembership(@RequestBody Membership membership) {
        membership.setId(null);
        membership.setMembershipStatus("ACTIVA");
        return memberships.save(membership);
    }

    @PutMapping("/memberships/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Membership updateMembership(@PathVariable Long id, @RequestBody Membership incoming) {
        Membership current = memberships.findById(id).orElseThrow();
        current.setPatientName(incoming.getPatientName());
        current.setPlanName(incoming.getPlanName());
        current.setTotalSessions(incoming.getTotalSessions());
        current.setUsedSessions(incoming.getUsedSessions());
        current.setPrice(incoming.getPrice());
        current.setPaidAmount(incoming.getPaidAmount());
        current.setStartDate(incoming.getStartDate());
        current.setEndDate(incoming.getEndDate());
        if (incoming.getMembershipStatus() != null) current.setMembershipStatus(validMembershipStatus(incoming.getMembershipStatus()));
        return memberships.save(current);
    }

    @PatchMapping("/memberships/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Membership membershipStatus(@PathVariable Long id, @RequestParam String value) {
        Membership membership = memberships.findById(id).orElseThrow();
        membership.setMembershipStatus(validMembershipStatus(value));
        return memberships.save(membership);
    }


    private String validMembershipStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return MEMBERSHIP_STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Estado de membresía no permitido"));
    }

    private String text(Object v) { return v == null ? "" : String.valueOf(v); }
    private BigDecimal decimal(Object v) { if (v == null) return BigDecimal.ZERO; try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.ZERO; } }
    private LocalDateTime dateTime(Object v) { if (v == null || String.valueOf(v).isBlank()) return null; try { return LocalDateTime.parse(String.valueOf(v)); } catch (Exception e) { return null; } }
    private LocalDate localDate(Object v) { if (v == null || String.valueOf(v).isBlank()) return null; try { return LocalDate.parse(String.valueOf(v)); } catch (Exception e) { return null; } }

}
