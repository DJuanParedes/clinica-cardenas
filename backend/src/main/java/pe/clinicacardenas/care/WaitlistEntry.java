package pe.clinicacardenas.care;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Paciente que espera un espacio en la agenda (se le avisa si alguien cancela). */
@Entity
@Table(name = "waitlist")
public class WaitlistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "dentist_id")
    public Long dentistId;
    public String reason;
    public String preference;
    public String status = "ACTIVA";
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @Column(name = "notified_at")
    public LocalDateTime notifiedAt;
    @Column(name = "closed_at")
    public LocalDateTime closedAt;
}
