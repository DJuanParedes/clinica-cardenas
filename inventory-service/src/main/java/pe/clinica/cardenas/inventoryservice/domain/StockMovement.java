package pe.clinica.cardenas.inventoryservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "STOCK_MOVEMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovement extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @Column(nullable = false)
    private Long itemId;
    @Column(nullable = false, length = 40)
    private String itemCode;
    @Column(nullable = false, length = 160)
    private String itemName;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MovementType movementType;
    @NotNull @Positive @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
    @NotBlank @Column(nullable = false, length = 300)
    private String reason;
    @Column(nullable = false, length = 120)
    private String responsible;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum MovementType { ENTRADA, SALIDA, AJUSTE_POSITIVO, AJUSTE_NEGATIVO }
}
