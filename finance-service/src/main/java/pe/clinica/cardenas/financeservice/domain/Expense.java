package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "EXPENSES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false, length = 80)
    private String category;
    @NotBlank @Column(nullable = false, length = 200)
    private String description;
    @NotNull @Positive @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(length = 120)
    private String supplier;
    @Column(length = 100)
    private String documentNumber;
    @Column(nullable = false, length = 120)
    private String registeredBy;
    @Column(nullable = false)
    private LocalDateTime registeredAt;
    @Column(nullable = false, length = 20)
    private String expenseStatus;
}
