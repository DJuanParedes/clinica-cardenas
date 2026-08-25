package pe.clinica.cardenas.authservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.domain.UserAccount;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;

import java.util.Map;

/**
 * Migra únicamente las tres cuentas demo históricas de versiones <= 4.8.1.
 * Nunca elimina una cuenta solo por correo: exige además nombre/rol originales y
 * ausencia de DNI/teléfono, datos que el flujo real actual obliga a registrar.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class LegacyDemoUserCleanupInitializer implements CommandLineRunner {
    private record DemoSignature(String fullName, Role role) {}
    private static final Map<String, DemoSignature> LEGACY_DEMO = Map.of(
            "doctor@cardenas.pe", new DemoSignature("Dra. Valeria Cárdenas", Role.DOCTOR),
            "recepcion@cardenas.pe", new DemoSignature("María Recepción", Role.RECEPCION),
            "finanzas@cardenas.pe", new DemoSignature("Carlos Finanzas", Role.FINANZAS)
    );

    private final UserAccountRepository users;

    @Override
    public void run(String... args) {
        LEGACY_DEMO.forEach((email, signature) -> users.findByEmail(email).ifPresent(account -> {
            if (isLegacyDemo(account, signature)) {
                users.delete(account);
                log.info("Cuenta demo histórica eliminada durante la migración segura: {}", email);
            } else {
                log.info("Se conserva la cuenta {} porque no coincide con la huella demo histórica", email);
            }
        }));
    }

    private boolean isLegacyDemo(UserAccount account, DemoSignature signature) {
        return account.getRole() == signature.role()
                && signature.fullName().equals(account.getFullName())
                && (account.getDni() == null || account.getDni().isBlank())
                && (account.getPhoneE164() == null || account.getPhoneE164().isBlank());
    }
}
