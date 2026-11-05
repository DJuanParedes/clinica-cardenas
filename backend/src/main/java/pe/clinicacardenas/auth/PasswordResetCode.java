package pe.clinicacardenas.auth;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id")
    public Long userId;
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
