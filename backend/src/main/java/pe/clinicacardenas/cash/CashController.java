package pe.clinicacardenas.cash;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.Dates;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HU-13, HU-14 y HU-15. */
@RestController
@RequestMapping("/api/cash")
public class CashController {

    private final CashService service;

    public CashController(CashService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return Map.of("methods", CashService.METHODS, "methodLabels", CashService.METHOD_LABEL,
                "expenseCategories", CashService.EXPENSE_CATEGORIES);
    }

    @GetMapping("/patients/{patientId}/account")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> account(@PathVariable Long patientId) {
        return service.balance(patientId);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> pay(@RequestBody CashService.PaymentRequest req) {
        return service.registerPayment(req);
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Payment voidPayment(@PathVariable Long id, @RequestBody CashService.VoidRequest req) {
        return service.voidPayment(id, req.reason());
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_EGRESOS')")
    public Expense expense(@RequestBody CashService.ExpenseRequest req) {
        return service.registerExpense(req);
    }

    @PostMapping("/expenses/{id}/void")
    @PreAuthorize("hasAuthority('CAJA_EGRESOS')")
    public Expense voidExpense(@PathVariable Long id, @RequestBody CashService.VoidRequest req) {
        return service.voidExpense(id, req.reason());
    }

    /** Caja del día: lo que cobró y pagó el turno. */
    @GetMapping("/today")
    @PreAuthorize("hasAnyAuthority('CAJA_COBRAR','CAJA_EGRESOS','CAJA_REPORTE')")
    public Map<String, Object> today() {
        LocalDate d = Dates.today();
        return service.report(d, d);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('CAJA_REPORTE')")
    public Map<String, Object> report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
