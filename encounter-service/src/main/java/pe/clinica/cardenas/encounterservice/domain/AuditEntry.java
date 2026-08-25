package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="AUDIT_ENTRIES") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEntry extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=80) private String module;
 @Column(nullable=false,length=80) private String entityType;
 @Column(length=80) private String entityId;
 @Column(nullable=false,length=40) private String action;
 @Column(nullable=false,length=120) private String actor;
 @Column(length=30) private String actorRole;
 @Lob private String beforeJson;
 @Lob private String afterJson;
 @Column(length=500) private String reason;
 @Column(nullable=false) private LocalDateTime occurredAt;
}
