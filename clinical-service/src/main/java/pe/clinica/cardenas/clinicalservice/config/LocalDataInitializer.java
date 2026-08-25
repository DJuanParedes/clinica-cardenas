package pe.clinica.cardenas.clinicalservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.clinicalservice.domain.*;
import pe.clinica.cardenas.clinicalservice.repository.*;

import java.math.BigDecimal;
import java.time.*;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final BudgetRepository budgets;
    private final ClinicalDocumentRepository documents;
    private final OdontogramRecordRepository odontograms;

    @Bean
    CommandLineRunner seedClinical() {
        return args -> {
            if (budgets.count() == 0) {
                budgets.save(Budget.builder().patientName("Andrea Flores Mendoza")
                        .doctorName("Dra. Valeria Cárdenas").treatment("Corona de zirconio pieza 16")
                        .versionNumber(1).subtotal(new BigDecimal("1650.00")).discount(new BigDecimal("100.00"))
                        .total(new BigDecimal("1550.00")).createdAt(LocalDate.now().minusDays(2))
                        .expiresAt(LocalDate.now().plusDays(28)).changeReason("Presupuesto inicial")
                        .budgetStatus("ENVIADO").build());
                budgets.save(Budget.builder().patientName("José Quispe Ramos")
                        .doctorName("Dr. Diego Salazar").treatment("Endodoncia + reconstrucción")
                        .versionNumber(2).subtotal(new BigDecimal("1280.00")).discount(new BigDecimal("80.00"))
                        .total(new BigDecimal("1200.00")).createdAt(LocalDate.now().minusDays(1))
                        .expiresAt(LocalDate.now().plusDays(20)).changeReason("Se agregó reconstrucción definitiva")
                        .budgetStatus("ACEPTADO").build());
            }
            if (documents.count() == 0) {
                documents.save(ClinicalDocument.builder().patientName("Andrea Flores Mendoza")
                        .category("Radiografía").fileName("panoramica-andrea-2026.pdf").version("v1")
                        .uploadedBy("Dra. Valeria Cárdenas").uploadedAt(LocalDateTime.now().minusDays(5))
                        .documentStatus("VIGENTE").build());
                documents.save(ClinicalDocument.builder().patientName("José Quispe Ramos")
                        .category("Consentimiento").fileName("consentimiento-endodoncia.pdf").version("v2")
                        .uploadedBy("María Recepción").uploadedAt(LocalDateTime.now().minusDays(1))
                        .documentStatus("FIRMADO").build());
            }
            if (odontograms.count() == 0) {
                odontograms.save(OdontogramRecord.builder().patientName("Andrea Flores Mendoza")
                        .stateJson("{\"16\":\"CORONA\",\"24\":\"RESTAURACIÓN\",\"36\":\"CARIES\"}")
                        .diagnosis("Preparación para rehabilitación protésica")
                        .treatment("Corona de zirconio").updatedBy("doctor@cardenas.pe")
                        .updatedAt(LocalDateTime.now().minusHours(2)).build());
            }
        };
    }
}
