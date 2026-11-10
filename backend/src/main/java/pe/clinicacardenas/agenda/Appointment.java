package pe.clinicacardenas.agenda;

import jakarta.persistence.*;
import pe.clinicacardenas.patient.Patient;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patient_id")
    public Patient patient;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;

    @Column(name = "start_at")
    public LocalDateTime startAt;
    @Column(name = "end_at")
    public LocalDateTime endAt;
    @Column(name = "duration_min")
    public int durationMin;

    public String reason;
    public String notes;

    @Enumerated(EnumType.STRING)
    public AppointmentStatus status;

    @Column(name = "status_reason")
    public String statusReason;
    @Column(name = "arrived_at")
    public LocalDateTime arrivedAt;

    /** El paciente pidió reprogramar desde WhatsApp (respondió "2"); recepción debe llamarlo. */
    @Column(name = "reschedule_requested_at")
    public LocalDateTime rescheduleRequestedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "updated_by")
    public String updatedBy;
}
