package pe.clinica.cardenas.inventoryservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "INVENTORY_ITEMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryItem extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false, unique = true, length = 40)
    private String code;
    @NotBlank @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 100)
    private String category;
    @Column(nullable = false, length = 30)
    private String unit;
    @NotNull @PositiveOrZero @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal currentStock;
    @NotNull @PositiveOrZero @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock;
    @Column(length = 60)
    private String lot;
    private LocalDate expiryDate;
    @PositiveOrZero @Column(precision = 14, scale = 2)
    private BigDecimal unitCost;
    @Column(nullable = false, length = 30)
    private String inventoryStatus;
    @Column(nullable = false)
    private boolean active;
}
