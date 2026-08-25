package pe.clinica.cardenas.authservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.clinica.cardenas.authservice.domain.RecoveryChannel;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.domain.UserAccount;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements CommandLineRunner {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bootstrap.admin.name}")
    private String fullName;
    @Value("${bootstrap.admin.email}")
    private String email;
    @Value("${bootstrap.admin.phone}")
    private String phone;
    @Value("${bootstrap.admin.recovery-channel}")
    private String recoveryChannel;

    @Override
    public void run(String... args) {
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = normalizePhone(phone);
        RecoveryChannel channel;
        try {
            channel = RecoveryChannel.valueOf(recoveryChannel.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_RECOVERY_CHANNEL debe ser EMAIL, SMS o WHATSAPP");
        }

        long administrators = repository.countByPlatformAdminTrue();
        if (administrators > 1) {
            throw new IllegalStateException("La plataforma exige exactamente un superadministrador global");
        }

        UserAccount configured = repository.findByEmail(normalizedEmail).orElse(null);
        if (configured != null) {
            if (configured.getRole() != Role.ADMIN || !configured.isPlatformAdmin() || administrators != 1) {
                throw new IllegalStateException("El correo configurado pertenece a otra cuenta o no es el superadministrador global");
            }
            synchronizeRecovery(configured, normalizedPhone, channel);
            return;
        }

        if (administrators == 1) {
            UserAccount current = repository.findByPlatformAdminTrue()
                    .orElseThrow(() -> new IllegalStateException("No se pudo localizar el superadministrador global"));
            current.setEmail(normalizedEmail);
            current.setPhoneE164(normalizedPhone);
            current.setRecoveryChannel(channel);
            invalidateSessionsAndResetRequests(current);
            repository.save(current);
            return;
        }

        repository.save(UserAccount.builder()
                .fullName(fullName.trim())
                .email(normalizedEmail)
                .phoneE164(normalizedPhone)
                .recoveryChannel(channel)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()))
                .role(Role.ADMIN)
                .clinicId("CARDENAS")
                .platformAdmin(true)
                .enabled(true)
                .mustResetPassword(true)
                .failedAttempts(0)
                .passwordChangedAt(null)
                .sessionVersion(0)
                .build());
    }

    private void synchronizeRecovery(UserAccount account, String normalizedPhone, RecoveryChannel channel) {
        boolean changed = false;
        if (!Objects.equals(account.getPhoneE164(), normalizedPhone)) {
            account.setPhoneE164(normalizedPhone);
            changed = true;
        }
        if (account.getRecoveryChannel() != channel) {
            account.setRecoveryChannel(channel);
            changed = true;
        }
        if (changed) {
            account.setLastPasswordResetRequestAt(null);
            account.setPasswordResetWindowStartedAt(null);
            account.setPasswordResetRequestCount(0);
            account.setPasswordResetFailureCount(0);
            account.setPasswordResetLockedUntil(null);
            repository.save(account);
        }
    }

    private void invalidateSessionsAndResetRequests(UserAccount account) {
        LocalDateTime now = LocalDateTime.now();
        account.setSessionInvalidatedAt(now);
        account.setSessionVersion(account.getSessionVersion() == null ? 1 : account.getSessionVersion() + 1);
        account.setLastPasswordResetRequestAt(null);
        account.setPasswordResetWindowStartedAt(null);
        account.setPasswordResetRequestCount(0);
        account.setPasswordResetFailureCount(0);
        account.setPasswordResetLockedUntil(null);
    }

    private String normalizePhone(String value) {
        if (value == null) throw new IllegalStateException("Debe configurar BOOTSTRAP_ADMIN_PHONE");
        String normalized = value.replace(" ", "").replace("-", "").trim();
        if (isPeruvianMobile(normalized)) normalized = "+51" + normalized;
        if (!isValidE164(normalized)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PHONE debe usar formato internacional, por ejemplo +51987654321");
        }
        return normalized;
    }

    private boolean isPeruvianMobile(String value) {
        return value.length() == 9 && value.charAt(0) == '9' && value.chars().allMatch(Character::isDigit);
    }

    private boolean isValidE164(String value) {
        if (value.length() < 9 || value.length() > 16 || value.charAt(0) != '+') return false;
        if (value.charAt(1) == '0') return false;
        return value.substring(1).chars().allMatch(Character::isDigit);
    }
}