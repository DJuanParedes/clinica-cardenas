package pe.clinicacardenas.portal;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.security.JwtService;

import java.util.Map;

/** HU-22: API de la aplicación móvil del paciente (SP-03: acceso según el rol del usuario). */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalService service;
    private final JwtService jwt;

    public PortalController(PortalService service, JwtService jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    @PostMapping("/auth/code")
    public Map<String, Object> code(@RequestBody PortalService.CodeRequest req) {
        return service.requestCode(req);
    }

    @PostMapping("/auth/activate")
    public Map<String, Object> activate(@RequestBody PortalService.ActivateRequest req) {
        return service.activate(req);
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody PortalService.LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String header) {
        jwt.revoke(header.substring(7));
        return Map.of("message", "Sesión cerrada.");
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        return service.profileOf(auth.getName());
    }

    @PatchMapping("/me/notifications")
    public Map<String, Object> channel(Authentication auth, @RequestBody PortalService.ChannelRequest req) {
        return service.updateChannel(auth.getName(), req.channel());
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(Authentication auth) {
        return service.appointments(auth.getName());
    }

    @PostMapping("/appointments/{id}/confirm")
    public Map<String, Object> confirm(Authentication auth, @PathVariable Long id) {
        return service.confirm(auth.getName(), id);
    }

    @GetMapping("/account")
    public Map<String, Object> account(Authentication auth) {
        return service.account(auth.getName());
    }
}
