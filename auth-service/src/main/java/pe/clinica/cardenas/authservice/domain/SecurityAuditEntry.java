package pe.clinica.cardenas.authservice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Append-only security evidence. Never stores credentials, OTPs or clinical data. */
@Entity
@Table(name = "SECURITY_AUDIT", indexes = @Index(name = "ix_security_audit_clinic_date", columnList = "clinic_id,occurred_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityAuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "clinic_id", nullable = false, length = 40) private String clinicId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 160) private String actor;
    @Column(nullable = false, length = 50) private String action;
    private Long userId;
    @Column(length = 30) private String previousRole;
    @Column(length = 30) private String newRole;
}
