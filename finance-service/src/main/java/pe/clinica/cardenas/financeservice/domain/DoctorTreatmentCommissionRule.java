package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "DOCTOR_TREATMENT_COMMISSION_RULES",
        uniqueConstraints = @UniqueConstraint(columnNames = {"doctorName", "treatmentName"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorTreatmentCommissionRule extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String doctorName;

    @NotBlank
    @Column(nullable = false, length = 180)
    private String treatmentName;

    @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 120)
    private String updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
