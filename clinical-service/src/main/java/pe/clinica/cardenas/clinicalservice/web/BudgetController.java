package pe.clinica.cardenas.clinicalservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.clinicalservice.domain.Budget;
import pe.clinica.cardenas.clinicalservice.repository.BudgetRepository;

import java.math.BigDecimal;
import java.util.Set;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private static final Set<String> MANUAL_STATUSES = Set.of("BORRADOR","ENVIADO","ACEPTADO","RECHAZADO","VENCIDO","ANULADO");
    private final BudgetRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','FINANZAS')")
    public List<Budget> list() { return repository.findAllByOrderByCreatedAtDesc(); }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public Budget create(@RequestBody Budget budget) {
        budget.setId(null);
        budget.setVersionNumber(1);
        budget.setCreatedAt(LocalDate.now());
        if (budget.getExpiresAt() == null) budget.setExpiresAt(LocalDate.now().plusDays(30));
        budget.setBudgetStatus("BORRADOR");
        if (budget.getChangeReason() == null || budget.getChangeReason().isBlank()) {
            budget.setChangeReason("Presupuesto inicial");
        }
        calculateTotal(budget);
        return repository.save(budget);
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasRole('DOCTOR')")
    public Budget createVersion(@PathVariable Long id, @RequestBody Budget changes) {
        Budget previous = repository.findById(id).orElseThrow();
        Budget version = Budget.builder()
                .patientName(previous.getPatientName())
                .doctorName(previous.getDoctorName())
                .treatment(changes.getTreatment() == null || changes.getTreatment().isBlank()
                        ? previous.getTreatment() : changes.getTreatment())
                .versionNumber(previous.getVersionNumber() + 1)
                .subtotal(changes.getSubtotal() == null ? previous.getSubtotal() : changes.getSubtotal())
                .discount(changes.getDiscount() == null ? previous.getDiscount() : changes.getDiscount())
                .createdAt(LocalDate.now())
                .expiresAt(changes.getExpiresAt() == null ? previous.getExpiresAt() : changes.getExpiresAt())
                .changeReason(changes.getChangeReason())
                .budgetStatus("MODIFICADO")
                .build();
        calculateTotal(version);
        previous.setBudgetStatus("REEMPLAZADO");
        repository.save(previous);
        return repository.save(version);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public Budget status(@PathVariable Long id, @RequestParam String value) {
        Budget budget = repository.findById(id).orElseThrow();
        budget.setBudgetStatus(validManualStatus(value));
        return repository.save(budget);
    }

    private void calculateTotal(Budget budget) {
        BigDecimal subtotal = budget.getSubtotal() == null ? BigDecimal.ZERO : budget.getSubtotal();
        BigDecimal discount = budget.getDiscount() == null ? BigDecimal.ZERO : budget.getDiscount();
        if (discount.compareTo(subtotal) > 0) throw new IllegalArgumentException("El descuento no puede superar el subtotal");
        budget.setSubtotal(subtotal);
        budget.setDiscount(discount);
        budget.setTotal(subtotal.subtract(discount));
    }
    private String validManualStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return MANUAL_STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de presupuesto no permitido"));
    }

}
