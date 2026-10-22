package pe.clinica.cardenas.authservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.authservice.domain.RecoveryChannel;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.domain.UserAccount;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;
import pe.clinica.cardenas.authservice.service.SchedulingProvisioningClient;
import pe.clinica.cardenas.security.TenantContext;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserAccountRepository repository;
    private final PasswordEncoder encoder;
    private final SchedulingProvisioningClient scheduling;
    private final pe.clinica.cardenas.authservice.service.SecurityAuditService audit;

    @GetMapping("/audit")
    public java.util.List<pe.clinica.cardenas.authservice.domain.SecurityAuditEntry> audit() { return audit.recent(); }

    @GetMapping
    public List<UserView> list() {
        return repository.findAllByClinicId(TenantContext.currentClinicId()).stream()
                .sorted(Comparator.comparing(UserAccount::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(UserView::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserView create(@Valid @RequestBody CreateUserRequest request, HttpServletRequest http, Authentication authentication) {
        if (request.role() == Role.ADMIN && !current(authentication).isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el superadministrador puede crear administradores de clínica");
        }
        if (request.role() == Role.ADMIN && repository.countByRoleAndClinicId(Role.ADMIN, TenantContext.currentClinicId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta clínica ya tiene un administrador");
        }
        String email = normalizeEmail(request.email());
        String dni = request.dni().trim();
        String phone = normalizePhone(request.phone());
        ensureUnique(null, email, dni, phone);
        validateDoctorFields(request.role(), request.doctorSpecialty(), request.doctorBranch(), request.shiftStart(), request.shiftEnd());
        UserAccount account = UserAccount.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .dni(dni)
                .phoneE164(phone)
                .recoveryChannel(request.recoveryChannel())
                .passwordHash(encoder.encode(dni))
                .role(request.role())
                .clinicId(TenantContext.currentClinicId())
                .platformAdmin(false)
                .enabled(true)
                .mustResetPassword(true)
                .failedAttempts(0)
                .passwordChangedAt(null)
                .build();
        UserAccount saved = repository.save(account);
        if (request.role() == Role.DOCTOR) {
            scheduling.ensureDoctor(null, saved.getFullName(), saved.getEmail(), request.doctorSpecialty().trim(), request.doctorBranch().trim(),
                    normalizedShiftStart(request.shiftStart()), normalizedShiftEnd(request.shiftEnd()), true, http.getHeader("Authorization"));
        }
        audit.record("USER_CREATED", saved, null);
        return UserView.from(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request, HttpServletRequest http) {
        UserAccount account = find(id);
        Role previousRole = account.getRole();
        String previousEmail = account.getEmail();
        if (previousRole == Role.ADMIN && request.role() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cambiar el rol del Administrador principal");
        }
        if (previousRole != Role.ADMIN && request.role() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pueden crear administradores adicionales");
        }
        if (previousRole == Role.ADMIN && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede deshabilitar el Administrador principal");
        }
        String email = normalizeEmail(request.email());
        if (!email.equalsIgnoreCase(account.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo es el identificador histórico de la cuenta y no se modifica. Si la cuenta aún no ingresó, elimínala y créala nuevamente con el correo correcto.");
        }
        String dni = cleanOptionalDni(request.dni(), previousRole);
        String phone = normalizePhone(request.phone());
        if (request.recoveryChannel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona SMS o WhatsApp como canal de recuperación");
        }
        ensureUnique(id, email, dni, phone);
        validateDoctorFields(request.role(), request.doctorSpecialty(), request.doctorBranch(), request.shiftStart(), request.shiftEnd());
        boolean initialAccessPending = account.isMustResetPassword();
        boolean dniChanged = dni != null && !dni.equals(account.getDni());
        account.setFullName(request.fullName().trim());
        account.setEmail(email);
        account.setDni(dni);
        account.setPhoneE164(phone);
        account.setRecoveryChannel(request.recoveryChannel());
        account.setRole(request.role());
        account.setEnabled(request.enabled());
        if (initialAccessPending && dniChanged) account.setPasswordHash(encoder.encode(dni));
        account.setSessionInvalidatedAt(LocalDateTime.now());
        account.setSessionVersion(sessionVersion(account) + 1);
        if (request.enabled()) { account.setFailedAttempts(0); account.setLockedUntil(null); }
        UserAccount saved = repository.save(account);
        String authorization = http.getHeader("Authorization");
        if (request.role() == Role.DOCTOR) {
            scheduling.ensureDoctor(previousRole == Role.DOCTOR ? previousEmail : null, saved.getFullName(), saved.getEmail(),
                    request.doctorSpecialty().trim(), request.doctorBranch().trim(), normalizedShiftStart(request.shiftStart()), normalizedShiftEnd(request.shiftEnd()),
                    request.enabled(), authorization);
        } else if (previousRole == Role.DOCTOR) {
            scheduling.setDoctorActive(previousEmail, false, authorization);
        }
        audit.record("USER_UPDATED", saved, previousRole);
        return UserView.from(saved);
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public UserView setEnabled(@PathVariable Long id, @RequestParam boolean value, HttpServletRequest http) {
        UserAccount account = find(id);
        if (account.getRole() == Role.ADMIN && !value) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede deshabilitar el Administrador principal");
        }
        account.setEnabled(value);
        account.setSessionInvalidatedAt(LocalDateTime.now());
        account.setSessionVersion(sessionVersion(account) + 1);
        if (value) { account.setFailedAttempts(0); account.setLockedUntil(null); }
        UserAccount saved = repository.save(account);
        if (saved.getRole() == Role.DOCTOR) scheduling.setDoctorActive(saved.getEmail(), value, http.getHeader("Authorization"));
        audit.record(value ? "USER_ENABLED" : "USER_DISABLED", saved, account.getRole());
        return UserView.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteBeforeFirstLogin(@PathVariable Long id, HttpServletRequest http) {
        UserAccount account = find(id);
        if (account.getRole() == Role.ADMIN) throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar el Administrador principal");
        if (account.getLastLoginAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Una cuenta que ya ingresó no se elimina; debe bloquearse para conservar trazabilidad");
        if (account.getRole() == Role.DOCTOR) scheduling.setDoctorActive(account.getEmail(), false, http.getHeader("Authorization"));
        audit.record("USER_DELETED_BEFORE_LOGIN", account, account.getRole());
        repository.delete(account);
    }

    @PostMapping("/{id}/reissue-initial-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reissueInitialAccess(@PathVariable Long id) {
        UserAccount account = find(id);
        if (account.getRole() == Role.ADMIN) throw new ResponseStatusException(HttpStatus.CONFLICT, "El Administrador no utiliza acceso temporal por DNI");
        if (!account.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Habilita la cuenta antes de reemitir el acceso inicial");
        if (account.getDni() == null || account.getPhoneE164() == null || account.getRecoveryChannel() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completa DNI, teléfono y canal de recuperación");
        }
        account.setPasswordHash(encoder.encode(account.getDni()));
        account.setMustResetPassword(true);
        account.setPasswordChangedAt(null);
        account.setSessionInvalidatedAt(LocalDateTime.now());
        account.setSessionVersion(sessionVersion(account) + 1);
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setPasswordResetFailureCount(0);
        account.setPasswordResetLockedUntil(null);
        repository.save(account);
        audit.record("INITIAL_ACCESS_REISSUED", account, account.getRole());
    }

    private void validateDoctorFields(Role role, String specialty, String branch, String shiftStart, String shiftEnd) {
        if (role != Role.DOCTOR) return;
        if (specialty == null || specialty.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la especialidad del doctor");
        if (branch == null || branch.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la sede del doctor");
        LocalTime start = parseTime(shiftStart, "Hora de inicio inválida");
        LocalTime end = parseTime(shiftEnd, "Hora de fin inválida");
        if (!end.isAfter(start) || start.isBefore(LocalTime.of(8,0)) || end.isAfter(LocalTime.of(22,0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario del doctor debe estar entre 08:00 y 22:00 y la hora fin debe ser posterior");
        }
    }
    private String normalizedShiftStart(String value) { return parseTime(value, "Hora de inicio inválida").toString(); }
    private String normalizedShiftEnd(String value) { return parseTime(value, "Hora de fin inválida").toString(); }
    private LocalTime parseTime(String value, String message) { try { return LocalTime.parse(value); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); } }

    private void ensureUnique(Long currentId, String email, String dni, String phone) {
        repository.findByEmail(email).filter(x -> currentId == null || !x.getId().equals(currentId))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado"); });
        if (dni != null) repository.findByDni(dni).filter(x -> currentId == null || !x.getId().equals(currentId))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El DNI ya está registrado"); });
        if (phone != null) repository.findByPhoneE164(phone).filter(x -> currentId == null || !x.getId().equals(currentId))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono ya está registrado para recuperación"); });
    }
    private int sessionVersion(UserAccount account) { return account.getSessionVersion() == null ? 0 : account.getSessionVersion(); }
    private UserAccount find(Long id) { return repository.findById(id)
            .filter(account -> TenantContext.currentClinicId().equalsIgnoreCase(account.getClinicId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")); }
    private UserAccount current(Authentication authentication) { return repository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida")); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(); }
    private String normalizePhone(String phone) {
        String v = phone == null ? "" : phone.replace(" ", "").replace("-", "").trim();
        if (v.matches("9\\d{8}")) v = "+51" + v;
        if (!v.matches("\\+[1-9]\\d{7,14}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teléfono inválido. Usa formato +519XXXXXXXX");
        return v;
    }
    private String cleanOptionalDni(String dni, Role role) {
        if (dni == null || dni.isBlank()) { if (role == Role.ADMIN) return null; throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI es obligatorio"); }
        String v = dni.trim(); if (!v.matches("\\d{8}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos"); return v;
    }

    public record UserView(Long id, String fullName, String email, String dni, String phone, RecoveryChannel recoveryChannel,
                           Role role, String clinicId, boolean platformAdmin, boolean enabled, boolean mustResetPassword, LocalDateTime lockedUntil,
                           LocalDateTime lastLoginAt, LocalDateTime passwordChangedAt) {
        static UserView from(UserAccount a) { return new UserView(a.getId(), a.getFullName(), a.getEmail(), a.getDni(), a.getPhoneE164(), a.getRecoveryChannel(), a.getRole(), a.getClinicId(), a.isPlatformAdmin(), a.isEnabled(), a.isMustResetPassword(), a.getLockedUntil(), a.getLastLoginAt(), a.getPasswordChangedAt()); }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "\\d{8}") String dni,
            @NotBlank @Size(max = 20) String phone,
            @NotNull RecoveryChannel recoveryChannel,
            @NotNull Role role,
            String doctorSpecialty, String doctorBranch, String shiftStart, String shiftEnd) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            String dni, String phone, RecoveryChannel recoveryChannel,
            @NotNull Role role, boolean enabled,
            String doctorSpecialty, String doctorBranch, String shiftStart, String shiftEnd) {}
}

