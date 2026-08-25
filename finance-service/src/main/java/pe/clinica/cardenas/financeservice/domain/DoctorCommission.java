package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "DOCTOR_COMMISSIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorCommission extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String doctorName;
    @Column(nullable = false, length = 30)
    private String period;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossProduction;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedProduction;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal laboratoryCosts;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;
    @Column(nullable = false, length = 30)
    private String paymentStatus;
}
