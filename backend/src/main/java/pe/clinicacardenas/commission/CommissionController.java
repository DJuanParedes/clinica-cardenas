package pe.clinicacardenas.commission;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HU-16, HU-17 y HU-18. */
@RestController
@RequestMapping("/api/commissions")
@PreAuthorize("hasAuthority('COMISIONES')")
public class CommissionController {

    private final CommissionService service;

    public CommissionController(CommissionService service) {
        this.service = service;
    }

    public record AnnulRequest(String reason) {}

    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        return service.summary();
    }

    @GetMapping("/rules")
    public List<CommissionRule> rules(@RequestParam(required = false) Long dentistId) {
        return service.listRules(dentistId);
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public CommissionRule create(@RequestBody CommissionService.RuleRequest req) {
        return service.saveRule(null, req);
    }

    @PutMapping("/rules/{id}")
    public CommissionRule update(@PathVariable Long id, @RequestBody CommissionService.RuleRequest req) {
        return service.saveRule(id, req);
    }

    @GetMapping("/calculate")
    public Map<String, Object> calculate(@RequestParam Long dentistId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calculate(dentistId, from, to);
    }

    @GetMapping("/settlements")
    public List<CommissionSettlement> settlements(@RequestParam(required = false) Long dentistId,
                                                  @RequestParam(required = false) String status) {
        return service.listSettlements(dentistId, status);
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> settle(@RequestBody CommissionService.SettleRequest req) {
        return service.settle(req);
    }

    @GetMapping("/settlements/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/settlements/{id}/payouts")
    public Map<String, Object> pay(@PathVariable Long id, @RequestBody CommissionService.PayoutRequest req) {
        return service.pay(id, req);
    }

    @PostMapping("/settlements/{id}/annul")
    public Map<String, Object> annul(@PathVariable Long id, @RequestBody AnnulRequest req) {
        return service.annul(id, req.reason());
    }
}
