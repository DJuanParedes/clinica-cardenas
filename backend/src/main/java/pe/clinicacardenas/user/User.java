package pe.clinicacardenas.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String username;
    public String email;

    @Column(name = "full_name")
    public String fullName;

    @JsonIgnore
    @Column(name = "password_hash")
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code")
    public Role role;

    public boolean active = true;

    @Column(name = "failed_attempts")
    public int failedAttempts;

    @Column(name = "locked_until")
    public LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    public LocalDateTime lastLoginAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "updated_by")
    public String updatedBy;

    public String getRoleLabel() {
        return role == null ? null : role.label;
    }
}
