package pe.clinica.cardenas.authservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.clinica.cardenas.authservice.domain.Clinic;
import pe.clinica.cardenas.authservice.domain.Role;
import pe.clinica.cardenas.authservice.repository.ClinicRepository;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;

import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ClinicInitializer implements CommandLineRunner {
    private final ClinicRepository clinics;
    private final UserAccountRepository users;

    @Override public void run(String... args) {
        Clinic cardenas = clinics.findByCodeIgnoreCase("CARDENAS").orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return clinics.save(Clinic.builder()
                    .code("CARDENAS").slug("cardenas").name("Clínica Cárdenas").shortName("Cárdenas")
                    .tagline("Atención odontológica en La Floresta")
                    .description("Atención odontológica para pacientes de La Floresta y zonas cercanas.")
                    .logoUrl("/brand/logo-cardenas.svg").heroImageUrl("/brand/cardenas-fachada.png")
                    .primaryColor("#3B78A6").primaryDarkColor("#245C80").accentColor("#D06B4F")
                    .accentDarkColor("#B8533C").sidebarDarkColor("#193D52")
                    .phone(null).email(null).address("La Floresta, Lima, Perú")
                    .workingHours("Atención previa coordinación").active(true).createdAt(now).updatedAt(now).build());
        });
        boolean clinicChanged = false;
        if ("Arequipa, Perú".equalsIgnoreCase(cardenas.getAddress())
                || "Ñaña, Lurigancho-Chosica, Lima, Perú".equalsIgnoreCase(cardenas.getAddress())) {
            cardenas.setAddress("La Floresta, Lima, Perú"); clinicChanged = true;
        }
        if ("+51 999 888 777".equals(cardenas.getPhone())) { cardenas.setPhone(null); clinicChanged = true; }
        if ("contacto@clinicacardenas.pe".equalsIgnoreCase(cardenas.getEmail())) { cardenas.setEmail(null); clinicChanged = true; }
        if ("Odontología especializada".equalsIgnoreCase(cardenas.getTagline())
                || "Atención odontológica en Ñaña".equalsIgnoreCase(cardenas.getTagline())) {
            cardenas.setTagline("Atención odontológica en La Floresta"); clinicChanged = true;
        }
        if ("Diagnóstico preciso, especialistas experimentados y seguimiento personalizado.".equals(cardenas.getDescription())
                || "Atención odontológica para pacientes de Ñaña y zonas cercanas.".equals(cardenas.getDescription())) {
            cardenas.setDescription("Atención odontológica para pacientes de La Floresta y zonas cercanas."); clinicChanged = true;
        }
        if ("Agenda operativa, 8:00 a 22:00".equalsIgnoreCase(cardenas.getWorkingHours())) {
            cardenas.setWorkingHours("Atención previa coordinación"); clinicChanged = true;
        }
        if (clinicChanged) { cardenas.setUpdatedAt(LocalDateTime.now()); cardenas = clinics.save(cardenas); }
        boolean platformAdminExists = users.countByPlatformAdminTrue() > 0;
        for (var user : users.findAll()) {
            boolean changed = false;
            if (user.getClinicId() == null || user.getClinicId().isBlank()) { user.setClinicId(cardenas.getCode()); changed = true; }
            if (!platformAdminExists && user.getRole() == Role.ADMIN) {
                user.setPlatformAdmin(true);
                platformAdminExists = true;
                changed = true;
            }
            if (changed) users.save(user);
        }
    }
}
