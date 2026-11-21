package pe.clinicacardenas.consent;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class InformedConsentController {

    private static final String SEND = "hasAnyAuthority('HISTORIA_CLINICA','PACIENTES_EDITAR')";
    private final InformedConsentService service;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public InformedConsentController(InformedConsentService service) {
        this.service = service;
    }

    @GetMapping("/informed-consents/templates")
    @PreAuthorize("hasAnyAuthority('HISTORIA_CLINICA','PACIENTES_EDITAR','CONSENTIMIENTOS')")
    public List<Map<String, Object>> templates(@RequestParam(defaultValue = "false") boolean all) {
        return service.templates(!all);
    }

    @PostMapping("/informed-consents/templates")
    @PreAuthorize("hasAuthority('CONSENTIMIENTOS')")
    public Map<String, Object> createTemplate(@RequestBody InformedConsentService.TemplateRequest r) { return service.saveTemplate(null, r); }

    @PutMapping("/informed-consents/templates/{id}")
    @PreAuthorize("hasAuthority('CONSENTIMIENTOS')")
    public Map<String, Object> updateTemplate(@PathVariable Long id, @RequestBody InformedConsentService.TemplateRequest r) { return service.saveTemplate(id, r); }

    @GetMapping("/informed-consents")
    @PreAuthorize(SEND)
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) { return service.list(status); }

    @GetMapping("/patients/{id}/informed-consents")
    @PreAuthorize(SEND)
    public List<Map<String, Object>> forPatient(@PathVariable Long id) { return service.forPatient(id); }

    @PostMapping("/patients/{id}/informed-consents")
    @PreAuthorize(SEND)
    public Map<String, Object> send(@PathVariable Long id, @RequestBody InformedConsentService.SendRequest r) { return service.send(id, r); }

    @PostMapping("/informed-consents/{id}/cancel")
    @PreAuthorize(SEND)
    public Map<String, Object> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) { return service.cancel(id, body.get("reason")); }

    // ---------- público (enlace firmado)

    @GetMapping("/public/consent/{token}")
    public Map<String, Object> open(@PathVariable String token) { return service.open(token); }

    @PostMapping("/public/consent/{token}")
    public Map<String, Object> sign(@PathVariable String token, @RequestBody InformedConsentService.SignRequest r, HttpServletRequest req) {
        String ip = ip(req);
        limit(ip);
        return service.sign(token, r, ip, req.getHeader("User-Agent"));
    }

    private static String ip(HttpServletRequest req) {
        String f = req.getHeader("X-Forwarded-For");
        return f != null && !f.isBlank() ? f.split(",")[0].trim() : req.getRemoteAddr();
    }

    /** Máximo 20 intentos de firma por hora desde la misma IP. */
    private void limit(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> d = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (d) {
            while (!d.isEmpty() && now - d.peekFirst() > 3_600_000) d.pollFirst();
            if (d.size() >= 20) throw new pe.clinicacardenas.common.ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", "Demasiados intentos. Espera un momento.");
            d.addLast(now);
        }
    }
}
