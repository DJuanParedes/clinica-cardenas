package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "MEMBERSHIPS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Membership extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 120)
    private String planName;
    private Integer totalSessions;
    private Integer usedSessions;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false, length = 30)
    private String membershipStatus;
}
