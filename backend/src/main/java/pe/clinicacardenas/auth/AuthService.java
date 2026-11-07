package pe.clinicacardenas.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.JwtService;
import pe.clinicacardenas.security.PermissionService;
import pe.clinicacardenas.user.User;
import pe.clinicacardenas.user.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final String GENERIC_RECOVER_MSG =
            "Si los datos corresponden a una cuenta activa, enviamos un código de 6 dígitos a su correo.";

    private final UserRepository users;
    private final DentistRepository dentists;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final MailNotifier mail;
    private final PermissionService permissions;
    private final int maxAttempts;
    private final int lockMinutes;
    private final boolean demoMode;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, DentistRepository dentists, PasswordResetCodeRepository codes,
                       PasswordEncoder encoder, JwtService jwt, AuditService audit, MailNotifier mail, PermissionService permissions,
                       @Value("${app.security.max-failed-attempts}") int maxAttempts,
                       @Value("${app.security.lock-minutes}") int lockMinutes,
                       @Value("${app.demo-mode}") boolean demoMode) {
        this.users = users;
        this.dentists = dentists;
        this.codes = codes;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.mail = mail;
        this.permissions = permissions;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
        this.demoMode = demoMode;
    }

    public record LoginResult(String token, long expiresInMinutes, Map<String, Object> user) {}

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password) {
        return login(username, password, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password, String code) {
        LocalDateTime now = Dates.now();
        User u = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (u == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos.");
        }
        if (!u.active) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUENTA_INACTIVA",
                    "Tu cuenta está desactivada. Comunícate con el administrador.");
        }
        if (u.lockedUntil != null && u.lockedUntil.isAfter(now)) {
            long min = Math.max(1, (Duration.between(now, u.lockedUntil).getSeconds() + 59) / 60);
            throw new ApiException(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                    "Cuenta bloqueada por intentos fallidos. Intenta de nuevo en " + min + " min o recupera tu contraseña.");
        }
        if (!encoder.matches(password, u.passwordHash)) {
            u.failedAttempts++;
            if (u.failedAttempts >= maxAttempts) {
                u.failedAttempts = 0;
                u.lockedUntil = now.plusMinutes(lockMinutes);
                users.save(u);
                audit.record("USUARIO", u.id, "BLOQUEO", "Bloqueo por " + maxAttempts + " intentos fallidos");
                throw new ApiException(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                        "Superaste " + maxAttempts + " intentos. La cuenta quedó bloqueada " + lockMinutes + " minutos.");
            }
            users.save(u);
            int left = maxAttempts - u.failedAttempts;
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                    "Usuario o contraseña incorrectos. Te " + (left == 1 ? "queda 1 intento." : "quedan " + left + " intentos."));
        }
        // Verificación en 2 pasos: además de la contraseña, el código de 6 dígitos de la app de autenticación
        if (u.totpEnabled) {
            if (code == null || code.isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "REQUIERE_2FA", "Ingresa el código de 6 dígitos de tu app de autenticación.");
            }
            if (!Totp.verify(u.totpSecret, code, System.currentTimeMillis() / 1000)) {
                u.failedAttempts++;
                users.save(u);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "CODIGO_2FA_INVALIDO", "El código no es correcto o ya venció. Usa el código actual de tu app.");
            }
        }
        u.failedAttempts = 0;
        u.lockedUntil = null;
        u.lastLoginAt = now;
        users.save(u);
        audit.record("USUARIO", u.id, "LOGIN", "Inicio de sesión");
        return new LoginResult(jwt.generate(u.username, u.role.name()), jwt.getExpirationMinutes(), profile(u));
    }

    // ------------------------------------------------------------------ verificación en 2 pasos

    private User user(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(() -> ApiException.notFound("El usuario"));
    }

    @Transactional
    public Map<String, Object> setupTotp(String username) {
        User u = user(username);
        if (u.totpEnabled) throw ApiException.conflict("2FA_ACTIVA", "La verificación en 2 pasos ya está activa.");
        u.totpSecret = Totp.newSecret();
        users.save(u);
        String label = java.net.URLEncoder.encode("Clínica Cárdenas:" + u.username, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("secret", u.totpSecret);
        m.put("otpauthUrl", "otpauth://totp/" + label + "?secret=" + u.totpSecret + "&issuer=Cl%C3%ADnica%20C%C3%A1rdenas&digits=6&period=30");
        return m;
    }

    @Transactional
    public Map<String, Object> enableTotp(String username, String code) {
        User u = user(username);
        if (u.totpSecret == null) throw ApiException.badRequest("SIN_CONFIGURAR", "Primero genera el código QR.");
        if (!Totp.verify(u.totpSecret, code, System.currentTimeMillis() / 1000)) {
            throw ApiException.field("code", "El código no coincide. Revisa la hora de tu teléfono e inténtalo de nuevo.");
        }
        u.totpEnabled = true;
        users.save(u);
        audit.record("USUARIO", u.id, "2FA_ACTIVADA", "Activó la verificación en 2 pasos");
        return Map.of("enabled", true, "message", "Verificación en 2 pasos activada.");
    }

    @Transactional
    public Map<String, Object> disableTotp(String username, String code) {
        User u = user(username);
        if (!u.totpEnabled) return Map.of("enabled", false);
        if (!Totp.verify(u.totpSecret, code, System.currentTimeMillis() / 1000)) {
            throw ApiException.field("code", "El código no es correcto.");
        }
        u.totpEnabled = false;
        u.totpSecret = null;
        users.save(u);
        audit.record("USUARIO", u.id, "2FA_DESACTIVADA", "Desactivó la verificación en 2 pasos");
        return Map.of("enabled", false, "message", "Verificación en 2 pasos desactivada.");
    }

    public Map<String, Object> profile(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("username", u.username);
        m.put("fullName", u.fullName);
        m.put("email", u.email);
        m.put("role", u.role.name());
        m.put("roleLabel", u.role.label);
        m.put("twoFactor", u.totpEnabled);
        dentists.findByUserId(u.id).ifPresent(d -> m.put("dentistId", d.id));
        m.put("permissions", permissions.forRole(u.role.name()));
        return m;
    }

    public Map<String, Object> me(String username) {
        User u = users.findByUsernameIgnoreCase(username).orElseThrow(() -> ApiException.notFound("El usuario"));
        return profile(u);
    }

    public void logout(String token, String username) {
        jwt.revoke(token);
        users.findByUsernameIgnoreCase(username).ifPresent(u -> audit.record("USUARIO", u.id, "LOGOUT", "Cierre de sesión"));
    }

    private Optional<User> findByLogin(String login) {
        String v = login.trim();
        return v.contains("@") ? users.findByEmailIgnoreCase(v) : users.findByUsernameIgnoreCase(v);
    }

    @Transactional
    public Map<String, Object> requestRecovery(String login) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", GENERIC_RECOVER_MSG);
        Optional<User> found = findByLogin(login);
        if (found.isEmpty() || !found.get().active) {
            return res; // misma respuesta: no se revela si la cuenta existe
        }
        User u = found.get();
        LocalDateTime now = Dates.now();
        for (PasswordResetCode old : codes.findByUserIdAndUsedAtIsNull(u.id)) {
            old.usedAt = now; // invalida códigos anteriores
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        PasswordResetCode prc = new PasswordResetCode();
        prc.userId = u.id;
        prc.codeHash = encoder.encode(code);
        prc.createdAt = now;
        prc.expiresAt = now.plusMinutes(15);
        codes.save(prc);
        mail.send(u.email, "Código para restablecer tu contraseña",
                "Hola " + u.fullName + ",\n\nTu código es: " + code + "\nVence en 15 minutos.\n\nClínica Dental Cárdenas");
        audit.record("USUARIO", u.id, "RECUPERACION_SOLICITADA", "Código de recuperación emitido");
        res.put("maskedEmail", mask(u.email));
        if (demoMode) {
            res.put("demoCode", code);
        }
        return res;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String login, String code, String newPassword) {
        PasswordPolicy.check("newPassword", newPassword);
        User u = findByLogin(login).filter(x -> x.active).orElseThrow(() ->
                ApiException.field("code", "El código no es válido o ya venció."));
        PasswordResetCode prc = codes.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(u.id).orElseThrow(() ->
                ApiException.field("code", "El código no es válido o ya venció."));
        LocalDateTime now = Dates.now();
        if (prc.expiresAt.isBefore(now) || prc.attempts >= 5) {
            prc.usedAt = now;
            codes.save(prc);
            throw ApiException.field("code", "El código venció. Solicita uno nuevo.");
        }
        if (!encoder.matches(code.trim(), prc.codeHash)) {
            prc.attempts++;
            codes.save(prc);
            throw ApiException.field("code", "El código no coincide. Revisa el correo e inténtalo otra vez.");
        }
        prc.usedAt = now;
        codes.save(prc);
        u.passwordHash = encoder.encode(newPassword);
        u.failedAttempts = 0;
        u.lockedUntil = null;
        u.updatedAt = now;
        u.updatedBy = u.username;
        users.save(u);
        audit.record("USUARIO", u.id, "CONTRASENA_RESTABLECIDA", "Contraseña restablecida con código");
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "•••" + email.substring(at - 1);
    }
}
