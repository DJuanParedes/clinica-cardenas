package pe.clinicacardenas.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.auth.AuthService;
import pe.clinicacardenas.security.CurrentUser;

import java.util.Map;

/** "Mi perfil": cualquier usuario del personal edita sus datos y cambia su contraseña. */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasRole('PERSONAL')")
public class ProfileController {

    private final UserService users;
    private final AuthService auth;

    public ProfileController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile() {
        return auth.me(CurrentUser.usernameOrSystem());
    }

    @PutMapping("/profile")
    public Map<String, Object> update(@RequestBody UserService.ProfileRequest r) {
        users.updateOwnProfile(r);
        return auth.me(CurrentUser.usernameOrSystem());
    }

    @PostMapping("/password")
    public Map<String, Object> password(@RequestBody UserService.PasswordChange r) {
        users.changeOwnPassword(r);
        return Map.of("message", "Contraseña actualizada.");
    }
}
