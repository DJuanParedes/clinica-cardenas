package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="SAVED_FILTERS") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedFilter extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(nullable=false,length=100) private String name;
 @NotBlank @Column(nullable=false,length=80) private String reportType;
 @Lob @Column(nullable=false) private String parametersJson;
 @Column(nullable=false,length=120) private String ownerEmail;
 @Column(nullable=false,length=30) private String ownerRole;
 @Column(nullable=false) private boolean shared;
 @Column(nullable=false) private LocalDateTime createdAt;
 private LocalDateTime lastRunAt;
}
