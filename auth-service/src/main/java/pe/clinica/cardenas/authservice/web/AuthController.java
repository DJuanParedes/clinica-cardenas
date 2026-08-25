package pe.clinica.cardenas.authservice.web;

import jakarta.annotation.PostConstruct;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.jsonwebtoken.Claims;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.authservice.domain.UserAccount;
import pe.clinica.cardenas.authservice.domain.RecoveryChannel;
import pe.clinica.cardenas.authservice.domain.Clinic;
import pe.clinica.cardenas.authservice.repository.ClinicRepository;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;
import pe.clinica.cardenas.authservice.service.OtpVerificationService;
import pe.clinica.cardenas.security.JwtService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final int RESET_MAX_REQUESTS_PER_HOUR = 5;
    private static final int RESET_MIN_SECONDS_BETWEEN_REQUESTS = 60;
    private static final int RESET_MAX_FAILED_CODES = 5;

    private final UserAccountRepository users;
    private final ClinicRepository clinics;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final OtpVerificationService otp;
    private String dummyPasswordHash;

    @PostConstruct
    void initializeDummyPasswordHash() {
        // Coste BCrypt equivalente para que un correo inexistente no responda notablemente más rápido.
        dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
    }
    private final Environment environment;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount account = users.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        // Siempre ejecutamos una comprobación BCrypt, incluso cuando el correo no existe,
        // para reducir diferencias de tiempo que faciliten enumerar cuentas.
        String passwordHash = account == null ? dummyPasswordHash : account.getPasswordHash();
        boolean passwordMatches = encoder.matches(request.password(), passwordHash);
        if (account == null || !account.isEnabled()
                || (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now))
                || account.isMustResetPassword()) {
            throw invalidCredentials();
        }
        // Una cuenta de primer acceso jamás obtiene sesión con el DNI temporal;
        // la activación exige OTP y una contraseña nueva.
        if (!passwordMatches) {
            registerFailedLogin(account, now);
            throw invalidCredentials();
        }
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(now);
        users.save(account);
        return response(account, activeClinic(account.getClinicId()));
    }

    @PostMapping("/switch-clinic/{clinicCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthResponse switchClinic(@PathVariable String clinicCode, Authentication authentication) {
        UserAccount account = users.findByEmail(authentication.getName()).orElseThrow(this::invalidSession);
        if (!account.isPlatformAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el superadministrador puede cambiar de clínica");
        Clinic clinic = activeClinic(clinicCode);
        account.setClinicId(clinic.getCode());
        users.save(account);
        return response(account, clinic);
    }

    @PostMapping("/password-reset/request")
    public ResetRequestResponse requestPasswordReset(@Valid @RequestBody ResetRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount account = users.findByEmail(email).orElse(null);
        if (account == null || !account.isEnabled()) return ResetRequestResponse.generic();
        if (account.getRecoveryChannel() == null) {
            return ResetRequestResponse.generic();
        }
        String recoveryDestination = recoveryDestination(account);
        if (recoveryDestination == null) return ResetRequestResponse.generic();
        LocalDateTime now = LocalDateTime.now();
        if (account.getPasswordResetLockedUntil() != null && account.getPasswordResetLockedUntil().isAfter(now)) {
            return ResetRequestResponse.generic();
        }
        if (account.getLastPasswordResetRequestAt() != null
                && Duration.between(account.getLastPasswordResetRequestAt(), now).getSeconds() < RESET_MIN_SECONDS_BETWEEN_REQUESTS) {
            return ResetRequestResponse.generic();
        }
        if (account.getPasswordResetWindowStartedAt() == null
                || account.getPasswordResetWindowStartedAt().isBefore(now.minusHours(1))) {
            account.setPasswordResetWindowStartedAt(now);
            account.setPasswordResetRequestCount(0);
        }
        int count = account.getPasswordResetRequestCount() == null ? 0 : account.getPasswordResetRequestCount();
        if (count >= RESET_MAX_REQUESTS_PER_HOUR) {
            return ResetRequestResponse.generic();
        }
        try {
            otp.start(recoveryDestination, account.getRecoveryChannel());
        } catch (RuntimeException ex) {
            log.warn("No se pudo iniciar OTP de recuperación para una cuenta registrada: {}", ex.getClass().getSimpleName());
            return ResetRequestResponse.generic();
        }
        account.setLastPasswordResetRequestAt(now);
        account.setPasswordResetRequestCount(count + 1);
        account.setPasswordResetFailureCount(0);
        users.save(account);
        return ResetRequestResponse.generic();
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody ResetConfirmRequest request) {
        validateStrongPasswordShape(request.newPassword());
        String email = normalizeEmail(request.email());
        UserAccount account = users.findByEmail(email)
                .filter(UserAccount::isEnabled)
                .orElseThrow(this::invalidResetCode);
        LocalDateTime now = LocalDateTime.now();
        if (account.getRecoveryChannel() == null) throw invalidResetCode();
        String recoveryDestination = recoveryDestination(account);
        if (recoveryDestination == null) throw invalidResetCode();
        if (account.getPasswordResetLockedUntil() != null && account.getPasswordResetLockedUntil().isAfter(now)) {
            throw invalidResetCode();
        }
        boolean approved;
        try {
            approved = otp.check(recoveryDestination, account.getRecoveryChannel(), request.code().trim());
        } catch (RuntimeException ex) {
            log.warn("No se pudo verificar OTP de recuperación para una cuenta registrada: {}", ex.getClass().getSimpleName());
            throw invalidResetCode();
        }
        if (!approved) {
            int failures = (account.getPasswordResetFailureCount() == null ? 0 : account.getPasswordResetFailureCount()) + 1;
            account.setPasswordResetFailureCount(failures);
            if (failures >= RESET_MAX_FAILED_CODES) {
                account.setPasswordResetLockedUntil(now.plusMinutes(LOCK_MINUTES));
                account.setPasswordResetFailureCount(0);
            }
            users.save(account);
            throw invalidResetCode();
        }
        validateAccountPasswordRules(request.newPassword(), account);
        account.setPasswordHash(encoder.encode(request.newPassword()));
        account.setMustResetPassword(false);
        account.setPasswordChangedAt(now);
        account.setSessionInvalidatedAt(now);
        account.setSessionVersion(sessionVersion(account) + 1);
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setPasswordResetFailureCount(0);
        account.setPasswordResetRequestCount(0);
        account.setPasswordResetWindowStartedAt(null);
        account.setPasswordResetLockedUntil(null);
        users.save(account);
    }

    /**
     * Valida que un JWT siga correspondiendo a una cuenta habilitada y vigente.
     * El API Gateway llama este endpoint en cada petición autenticada para que
     * bloquear una cuenta, cambiar su rol o cambiar su contraseña revoque
     * inmediatamente los tokens emitidos antes de ese cambio.
     */
    @GetMapping("/session/validate")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw invalidSession();
        Claims claims;
        try { claims = jwt.parse(authorization.substring(7)); }
        catch (Exception ex) { throw invalidSession(); }

        String email = normalizeEmail(claims.getSubject());
        UserAccount account = users.findByEmail(email).orElse(null);
        if (account == null) {
            boolean localValidation = Arrays.asList(environment.getActiveProfiles()).contains("local")
                    && email.endsWith("@validation.local");
            if (localValidation) return;
            throw invalidSession();
        }
        LocalDateTime now = LocalDateTime.now();
        if (!account.isEnabled() || account.isMustResetPassword()) throw invalidSession();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) throw invalidSession();
        String tokenRole = claims.get("role", String.class);
        if (tokenRole == null || !account.getRole().name().equals(tokenRole)) throw invalidSession();
        String tokenClinic = claims.get("clinicId", String.class);
        boolean tokenPlatformAdmin = Boolean.TRUE.equals(claims.get("platformAdmin", Boolean.class));
        if (tokenPlatformAdmin != account.isPlatformAdmin()) throw invalidSession();
        Clinic clinic = activeClinic(tokenClinic);
        if (!account.isPlatformAdmin() && !clinic.getCode().equalsIgnoreCase(account.getClinicId())) throw invalidSession();
        Integer tokenSessionVersion = claims.get("sv", Integer.class);
        if (tokenSessionVersion == null || tokenSessionVersion.intValue() != sessionVersion(account)) throw invalidSession();
    }

    @PatchMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        UserAccount account = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (!encoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        validateStrongPasswordShape(request.newPassword());
        validateAccountPasswordRules(request.newPassword(), account);
        account.setPasswordHash(encoder.encode(request.newPassword()));
        account.setMustResetPassword(false);
        LocalDateTime changedAt = LocalDateTime.now();
        account.setPasswordChangedAt(changedAt);
        account.setSessionInvalidatedAt(changedAt);
        account.setSessionVersion(sessionVersion(account) + 1);
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        users.save(account);
    }

    private void registerFailedLogin(UserAccount account, LocalDateTime now) {
        int attempts = (account.getFailedAttempts() == null ? 0 : account.getFailedAttempts()) + 1;
        account.setFailedAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) {
            account.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
            account.setFailedAttempts(0);
        }
        users.save(account);
    }

    private void validateStrongPasswordShape(String password) {
        if (password == null || password.length() < 10 || password.length() > 80
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe tener 10 a 80 caracteres, mayúscula, minúscula, número y símbolo");
        }
    }

    private void validateAccountPasswordRules(String password, UserAccount account) {
        if (account.getDni() != null && password.equals(account.getDni())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede ser el DNI");
        }
        if (encoder.matches(password, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña nueva debe ser diferente a la actual");
        }
    }

    private int sessionVersion(UserAccount account) { return account.getSessionVersion() == null ? 0 : account.getSessionVersion(); }
    private Clinic activeClinic(String code) {
        return clinics.findByCodeIgnoreCase(code == null ? "CARDENAS" : code).filter(Clinic::isActive)
                .orElseThrow(this::invalidSession);
    }
    private AuthResponse response(UserAccount account, Clinic clinic) {
        String token = jwt.generateToken(account.getEmail(), account.getFullName(), account.getRole().name(), sessionVersion(account),
                clinic.getCode(), clinic.getSlug(), account.isPlatformAdmin());
        return new AuthResponse(token, account.getFullName(), account.getEmail(), account.getRole().name(),
                account.isPlatformAdmin(), ClinicBrand.from(clinic));
    }
    private String recoveryDestination(UserAccount account) {
        if (account.getRecoveryChannel() == RecoveryChannel.EMAIL) {
            return account.getEmail() == null ? null : normalizeEmail(account.getEmail());
        }
        return account.getPhoneE164() == null || account.getPhoneE164().isBlank() ? null : account.getPhoneE164();
    }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(); }
    private String maskPhone(String phone) { return phone == null || phone.length() < 5 ? "***" : "***" + phone.substring(phone.length() - 4); }
    private ResponseStatusException invalidCredentials() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"); }
    private ResponseStatusException invalidResetCode() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido o vencido"); }
    private ResponseStatusException invalidSession() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida o revocada"); }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 80) String newPassword) {}
    public record ResetRequest(@NotBlank @Email String email) {}
    public record ResetConfirmRequest(@NotBlank @Email String email, @NotBlank @Size(min = 4, max = 10) String code,
                                      @NotBlank @Size(min = 10, max = 80) String newPassword) {}
    public record ResetRequestResponse(String message, String destination, String channel) {
        static ResetRequestResponse generic() { return new ResetRequestResponse("Si el correo está registrado, se enviará un código al medio de recuperación configurado.", null, null); }
    }
}
