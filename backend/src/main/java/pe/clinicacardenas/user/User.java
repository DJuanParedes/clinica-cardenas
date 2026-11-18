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

    public String phone;

    /** Rol de acceso personalizado (define los permisos). Si es nulo, se usan los permisos del puesto base. */
    @Column(name = "access_role_code")
    public String accessRole;

    /** APP (Google Authenticator) o CORREO (código enviado al correo). Nulo = sin verificación en 2 pasos. */
    @Column(name = "two_factor_method")
    public String twoFactorMethod;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "email_otp_hash")
    public String emailOtpHash;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "email_otp_expires")
    public LocalDateTime emailOtpExpires;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "email_otp_attempts")
    public int emailOtpAttempts;

    /** Código del rol cuyos permisos aplican a este usuario. */
    public String effectiveRole() {
        return accessRole != null && !accessRole.isBlank() ? accessRole : role.name();
    }

    public boolean active = true;

    @Column(name = "failed_attempts")
    public int failedAttempts;

    @Column(name = "locked_until")
    public LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    public LocalDateTime lastLoginAt;

    /** Verificación en 2 pasos (TOTP). */
    @JsonIgnore
    @Column(name = "totp_secret")
    public String totpSecret;
    @Column(name = "totp_enabled")
    public boolean totpEnabled;

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
