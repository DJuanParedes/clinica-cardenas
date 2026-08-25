package pe.clinica.cardenas.clinicalservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "BUDGETS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 120)
    private String doctorName;
    @Column(nullable = false, length = 200)
    private String treatment;
    @Column(nullable = false)
    private Integer versionNumber;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    private LocalDate createdAt;
    private LocalDate expiresAt;
    @Column(length = 350)
    private String changeReason;
    @Column(nullable = false, length = 40)
    private String budgetStatus;
}
