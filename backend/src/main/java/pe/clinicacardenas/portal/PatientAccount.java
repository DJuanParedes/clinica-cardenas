package pe.clinicacardenas.portal;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** HU-22: acceso del paciente a la aplicación móvil. */
@Entity
@Table(name = "patient_accounts")
public class PatientAccount {
    /** Prefijo del "subject" del token JWT del paciente; lo distingue de las cuentas del personal. */
    public static final String SUBJECT_PREFIX = "pac:";

    @Id
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "password_hash")
    public String passwordHash;
    public boolean active = true;
    @Column(name = "failed_attempts")
    public int failedAttempts;
    @Column(name = "locked_until")
    public LocalDateTime lockedUntil;
    @Column(name = "last_login_at")
    public LocalDateTime lastLoginAt;
    @Column(name = "activated_at")
    public LocalDateTime activatedAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
