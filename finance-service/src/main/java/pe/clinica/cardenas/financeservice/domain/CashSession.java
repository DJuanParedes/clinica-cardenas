package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CASH_SESSIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashSession extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false, length = 80)
    private String branch;
    @Column(nullable = false, length = 120)
    private String cashier;
    @NotNull @PositiveOrZero @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal expectedAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal actualAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal difference;
    @Column(nullable = false)
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    @Column(nullable = false, length = 20)
    private String cashStatus;
    @Column(length = 400)
    private String notes;
}
