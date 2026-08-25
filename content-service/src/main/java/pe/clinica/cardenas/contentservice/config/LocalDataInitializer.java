package pe.clinica.cardenas.contentservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.contentservice.domain.PublicInquiry;
import pe.clinica.cardenas.contentservice.repository.PublicInquiryRepository;
import java.time.LocalDateTime;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final PublicInquiryRepository repository;
    @Bean CommandLineRunner seedInquiries() {
        return args -> {
            if (repository.count() == 0) repository.save(PublicInquiry.builder()
                    .fullName("Lucía Torres Medina").phone("987654321").specialty("Ortodoncia")
                    .message("Solicita evaluación por la tarde.").createdAt(LocalDateTime.now().minusHours(3))
                    .status("CONTACTADA").assignedTo("María Recepción").build());
        };
    }
}
