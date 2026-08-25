package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import lombok.*; import java.math.BigDecimal; import java.time.LocalDate;
@Entity @Table(name="CHAIR_GOALS") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChairGoal extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(nullable=false,length=60) private String chairName;
 @NotBlank @Column(nullable=false,length=20) private String periodType;
 @NotNull @Column(nullable=false) private LocalDate periodStart;
 @Column(precision=14,scale=2) private BigDecimal productionGoal;
 @Column(precision=14,scale=2) private BigDecimal collectionGoal;
 private Integer utilizationGoal;
 private Integer patientGoal;
 @Column(nullable=false) private boolean active;
}
