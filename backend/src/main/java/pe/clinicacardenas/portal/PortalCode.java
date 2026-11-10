package pe.clinicacardenas.portal;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_codes")
public class PortalCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "code_hash")
    public String codeHash;
    public int attempts;
    @Column(name = "expires_at")
    public LocalDateTime expiresAt;
    @Column(name = "used_at")
    public LocalDateTime usedAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
