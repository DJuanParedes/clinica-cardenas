package pe.clinicacardenas.care;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CareController {

    private final CareService service;
    private final String webhookToken;

    public CareController(CareService service, @Value("${app.notifications.webhook-token}") String webhookToken) {
        this.service = service;
        this.webhookToken = webhookToken;
    }

    @GetMapping("/waitlist")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public List<Map<String, Object>> waitlist() {
        return service.waitlist();
    }

    @PostMapping("/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public WaitlistEntry add(@RequestBody CareService.WaitRequest req) {
        return service.addToWaitlist(req);
    }

    @PostMapping("/waitlist/{id}/close")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public WaitlistEntry close(@PathVariable Long id) {
        return service.closeWaitlist(id);
    }

    @GetMapping("/plans/{planId}/installments")
    @PreAuthorize("hasAnyAuthority('PLAN_TRATAMIENTO','CAJA_COBRAR','HISTORIA_CLINICA')")
    public Map<String, Object> installments(@PathVariable Long planId) {
        return service.installments(planId);
    }

    @PostMapping("/plans/{planId}/installments")
    @PreAuthorize("hasAnyAuthority('PLAN_TRATAMIENTO','CAJA_COBRAR')")
    public Map<String, Object> createInstallments(@PathVariable Long planId, @RequestBody CareService.InstallmentRequest req) {
        return service.createInstallments(planId, req);
    }

    @GetMapping("/recalls")
    @PreAuthorize("hasAnyAuthority('CITAS_GESTIONAR','REPORTES')")
    public List<Map<String, Object>> recalls() {
        return service.recalls();
    }

    @PostMapping("/recalls/{patientId}/send")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public Map<String, Object> sendRecall(@PathVariable Long patientId, @RequestBody(required = false) Map<String, String> body) {
        return service.sendRecall(patientId, body == null ? null : body.get("kind"));
    }

    @GetMapping("/agenda/reschedule-requests")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public List<Map<String, Object>> rescheduleRequests() {
        return service.rescheduleRequests();
    }

    @GetMapping("/reports/satisfaction")
    @PreAuthorize("hasAnyAuthority('REPORTES','AGENDA_VER')")
    public Map<String, Object> satisfaction(@RequestParam(defaultValue = "90") int days) {
        return service.satisfaction(days);
    }

    /** Respuestas del paciente por WhatsApp (las reenvía el microservicio de notificaciones). */
    @PostMapping("/internal/whatsapp/reply")
    public Map<String, Object> reply(@RequestHeader(value = "X-Webhook-Token", required = false) String token,
                                     @RequestBody CareService.ReplyRequest req) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), webhookToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO", "Token del webhook inválido.");
        }
        return service.whatsappReply(req);
    }
}
