package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import lombok.*; import java.math.BigDecimal;
@Entity @Table(name="TREATMENT_CATALOG") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TreatmentCatalog extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(nullable=false,unique=true,length=120) private String name;
 @Column(length=30) private String category;
 @Column(length=80) private String specialty;
 @NotNull @Min(10) @Max(480) @Column(nullable=false) private Integer defaultDurationMinutes;
 @DecimalMin("0.00") @Column(precision=14,scale=2) private BigDecimal referencePrice;
 @Column(nullable=false) private boolean active;
}
