package pe.clinicacardenas.cash;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.Dates;

import java.time.LocalDate;
import java.util.Map;

/** HU-13, HU-14 y HU-15. */
@RestController
@RequestMapping("/api/cash")
public class CashController {

    private final CashService service;
    private final CashClosingService closings;

    public CashController(CashService service, CashClosingService closings) {
        this.closings = closings;
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
        closings.ensureOpen(Dates.today());
        return service.registerPayment(req);
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Payment voidPayment(@PathVariable Long id, @RequestBody CashService.VoidRequest req) {
        closings.ensurePaymentDayOpen(id);
        return service.voidPayment(id, req.reason());
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_EGRESOS')")
    public Expense expense(@RequestBody CashService.ExpenseRequest req) {
        closings.ensureOpen(req.expenseDate() == null ? Dates.today() : req.expenseDate());
        return service.registerExpense(req);
    }

    @PostMapping("/expenses/{id}/void")
    @PreAuthorize("hasAuthority('CAJA_EGRESOS')")
    public Expense voidExpense(@PathVariable Long id, @RequestBody CashService.VoidRequest req) {
        closings.ensureExpenseDayOpen(id);
        return service.voidExpense(id, req.reason());
    }

    // ---------- v2.10: cuadre y cierre de caja

    @GetMapping("/closing")
    @PreAuthorize("hasAnyAuthority('CAJA_CIERRE','CAJA_REPORTE')")
    public Map<String, Object> closing(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return closings.summary(date);
    }

    @PostMapping("/closing")
    @PreAuthorize("hasAuthority('CAJA_CIERRE')")
    public Map<String, Object> close(@RequestBody CashClosingService.CloseRequest req) {
        return closings.close(req);
    }

    @PostMapping("/closing/{date}/reopen")
    @PreAuthorize("hasAuthority('CAJA_REABRIR')")
    public Map<String, Object> reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestBody CashClosingService.ReopenRequest req) {
        return closings.reopen(date, req);
    }

    @GetMapping("/closings")
    @PreAuthorize("hasAnyAuthority('CAJA_CIERRE','CAJA_REPORTE')")
    public java.util.List<Map<String, Object>> closings(@RequestParam(defaultValue = "30") int limit) {
        return closings.history(limit);
    }

    @GetMapping("/closing/{date}/pdf")
    @PreAuthorize("hasAnyAuthority('CAJA_CIERRE','CAJA_REPORTE')")
    public org.springframework.http.ResponseEntity<byte[]> closingPdf(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cierre-caja-" + date + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF).body(closings.pdf(date));
    }

    /** Caja del día: lo que cobró y pagó el turno. */
    @GetMapping("/today")
    @PreAuthorize("hasAnyAuthority('CAJA_COBRAR','CAJA_EGRESOS','CAJA_REPORTE')")
    public Map<String, Object> today() {
        LocalDate d = Dates.today();
        return service.report(d, d);
    }

    /** Historial: la caja de cualquier día anterior (hasta un año atrás), no solo la de hoy. */
    @GetMapping("/day")
    @PreAuthorize("hasAnyAuthority('CAJA_COBRAR','CAJA_EGRESOS','CAJA_REPORTE')")
    public Map<String, Object> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date.isAfter(Dates.today())) throw pe.clinicacardenas.common.ApiException.field("date", "La fecha no puede ser futura.");
        if (date.isBefore(Dates.today().minusYears(1))) throw pe.clinicacardenas.common.ApiException.field("date", "Para más de un año usa el reporte de flujo de caja.");
        return service.report(date, date);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('CAJA_REPORTE')")
    public Map<String, Object> report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
