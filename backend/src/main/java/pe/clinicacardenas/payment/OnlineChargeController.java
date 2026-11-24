package pe.clinicacardenas.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.portal.PortalService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Cobros en línea con Mercado Pago: caja, portal/app del paciente y el webhook público. */
@RestController
public class OnlineChargeController {

    private final OnlineChargeService service;

    public OnlineChargeController(OnlineChargeService service) {
        this.service = service;
    }

    // ---------- caja (personal con permiso de cobro)

    @GetMapping("/api/cash/online/config")
    public Map<String, Object> config() {
        return service.config();
    }

    @PostMapping("/api/cash/online")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> create(@RequestBody OnlineChargeService.CreateRequest req) {
        return service.create(req, "CAJA");
    }

    @GetMapping("/api/cash/online")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public List<Map<String, Object>> recent(@RequestParam(required = false) Long patientId) {
        return service.recent(patientId);
    }

    @GetMapping("/api/cash/online/{id}")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> status(@PathVariable long id) {
        return service.status(id, null);
    }

    @PostMapping("/api/cash/online/{id}/cancel")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @PostMapping("/api/cash/online/{id}/whatsapp")
    @PreAuthorize("hasAuthority('CAJA_COBRAR')")
    public Map<String, Object> whatsapp(@PathVariable long id) {
        return service.sendWhatsapp(id);
    }

    // ---------- paciente (portal web y app)

    public record PortalRequest(BigDecimal amount) {}

    @GetMapping("/api/portal/payments/online/config")
    public Map<String, Object> portalConfig() {
        return service.config();
    }

    @PostMapping("/api/portal/payments/online")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> portalCreate(Authentication auth, @RequestBody PortalRequest req) {
        long patientId = PortalService.patientIdFrom(auth.getName());
        return service.createForPatient(patientId, req.amount());
    }

    @GetMapping("/api/portal/payments/online/{id}")
    public Map<String, Object> portalStatus(Authentication auth, @PathVariable long id) {
        return service.status(id, PortalService.patientIdFrom(auth.getName()));
    }

    // ---------- webhook de Mercado Pago (público: valida firma y verifica el pago con la API)

    @PostMapping("/api/public/mercadopago/webhook")
    public Map<String, Object> webhook(@RequestParam(name = "type", required = false) String type,
                                       @RequestParam(name = "topic", required = false) String topic,
                                       @RequestParam(name = "data.id", required = false) String dataId,
                                       @RequestParam(name = "id", required = false) String legacyId,
                                       @RequestHeader(name = "x-signature", required = false) String signature,
                                       @RequestHeader(name = "x-request-id", required = false) String requestId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String t = type != null ? type : topic;
        String id = dataId;
        if (id == null && body != null && body.get("data") instanceof Map<?, ?> d && d.get("id") != null) id = String.valueOf(d.get("id"));
        if (t == null && body != null && body.get("type") != null) t = String.valueOf(body.get("type"));
        if (id == null && "payment".equals(t)) id = legacyId;
        service.webhook(t, id, signature, requestId);
        return Map.of("ok", true);
    }
}
