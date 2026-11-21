package pe.clinicacardenas.portal;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Encuesta de satisfacción de una cita atendida (1 a 5 estrellas). */
@Entity
@Table(name = "surveys")
public class Survey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "appointment_id")
    public Long appointmentId;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "dentist_id")
    public Long dentistId;
    public int score;
    public String comment;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
