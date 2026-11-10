package pe.clinicacardenas.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank(message = "Ingresa tu usuario.") String username,
                               @NotBlank(message = "Ingresa tu contraseña.") String password,
                               String code) {}

    public record CodeRequest(String code) {}

    public record RecoverRequest(@NotBlank(message = "Ingresa tu usuario o correo.") String login) {}

    public record ResetRequest(@NotBlank(message = "Ingresa tu usuario o correo.") String login,
                               @NotBlank(message = "Ingresa el código recibido.") String code,
                               @NotBlank(message = "Ingresa la nueva contraseña.") String newPassword) {}

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password(), req.code());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return auth.me(authentication.getName());
    }

    @PostMapping("/2fa/setup")
    public Map<String, Object> setup2fa(Authentication a) {
        return auth.setupTotp(a.getName());
    }

    @PostMapping("/2fa/enable")
    public Map<String, Object> enable2fa(Authentication a, @RequestBody CodeRequest req) {
        return auth.enableTotp(a.getName(), req.code());
    }

    @PostMapping("/2fa/disable")
    public Map<String, Object> disable2fa(Authentication a, @RequestBody CodeRequest req) {
        return auth.disableTotp(a.getName(), req.code());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String header, Authentication a) {
        auth.logout(header.substring(7), a.getName());
        return Map.of("message", "Sesión cerrada.");
    }

    @PostMapping("/recover")
    public Map<String, Object> recover(@Valid @RequestBody RecoverRequest req) {
        return auth.requestRecovery(req.login());
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetRequest req) {
        auth.resetPassword(req.login(), req.code(), req.newPassword());
        return Map.of("message", "Contraseña actualizada. Ya puedes iniciar sesión.");
    }
}
