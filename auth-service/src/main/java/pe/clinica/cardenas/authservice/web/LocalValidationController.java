package pe.clinica.cardenas.authservice.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;
import pe.clinica.cardenas.security.JwtService;

import java.util.Map;

@RestController
@RequestMapping("/api/users/local-validation")
@Profile("local")
@PreAuthorize("hasRole('ADMIN')")
public class LocalValidationController {
    private final JwtService jwt;
    private final UserAccountRepository users;
    public LocalValidationController(JwtService jwt, UserAccountRepository users) { this.jwt = jwt; this.users = users; }

    @PostMapping("/token")
    public Map<String,String> token(@RequestBody ValidationTokenRequest request) {
        if (request.role() == null || request.role() == Role.ADMIN) throw new IllegalArgumentException("Rol de validación inválido");
        String name = request.name() == null || request.name().isBlank() ? "Usuario Validación" : request.name().trim();
        String email = request.email() == null || request.email().isBlank() ? request.role().name().toLowerCase()+"@validation.local" : request.email().trim().toLowerCase();
        return Map.of("token", jwt.generateToken(email, name, request.role().name()), "name", name, "email", email, "role", request.role().name());
    }

    /** Solo para la suite local: elimina cuentas temporales creadas por las pruebas. */
    @DeleteMapping("/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@RequestParam String email) {
        users.findByEmail(email.trim().toLowerCase()).ifPresent(account -> {
            if (account.getRole() == Role.ADMIN) throw new IllegalArgumentException("No se puede purgar ADMIN");
            users.delete(account);
        });
    }
    public record ValidationTokenRequest(Role role, String name, String email) {}
}
