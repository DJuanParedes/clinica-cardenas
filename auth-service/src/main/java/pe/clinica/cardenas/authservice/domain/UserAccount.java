package pe.clinica.cardenas.authservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "APP_USERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(unique = true, length = 8)
    private String dni;

    @Column(unique = true, length = 20)
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RecoveryChannel recoveryChannel;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "clinic_id", nullable = false, length = 40,
            columnDefinition = "varchar(40) default 'CARDENAS'")
    @Builder.Default
    private String clinicId = "CARDENAS";

    @Column(nullable = false)
    @Builder.Default
    private boolean platformAdmin = false;

    @Column(nullable = false)
    private boolean enabled;

    /** Nullable para migrar de versiones anteriores sin romper filas existentes. */
    @Builder.Default
    private Boolean mustResetPassword = false;

    @Builder.Default
    private Integer failedAttempts = 0;

    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime sessionInvalidatedAt;

    @Builder.Default
    private Integer sessionVersion = 0;

    private LocalDateTime lastPasswordResetRequestAt;
    private LocalDateTime passwordResetWindowStartedAt;

    @Builder.Default
    private Integer passwordResetRequestCount = 0;

    @Builder.Default
    private Integer passwordResetFailureCount = 0;

    private LocalDateTime passwordResetLockedUntil;

    public boolean isMustResetPassword() { return Boolean.TRUE.equals(mustResetPassword); }
    public void setMustResetPassword(boolean value) { this.mustResetPassword = value; }
}
