package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="BACKUP_VERIFICATIONS") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupVerification extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(nullable=false,length=120) private String backupName;
 @NotBlank @Column(nullable=false,length=80) private String scope;
 @NotBlank @Column(nullable=false,length=240) private String location;
 private Long sizeMb;
 @Column(length=128) private String checksum;
 @Column(nullable=false,length=30) private String status;
 @Column(nullable=false) private boolean encrypted;
 @Column(nullable=false) private boolean offsiteCopy;
 private LocalDateTime completedAt;
 private LocalDateTime restorationTestedAt;
 @Column(length=1200) private String notes;
 @Column(nullable=false,length=120) private String responsible;
}
