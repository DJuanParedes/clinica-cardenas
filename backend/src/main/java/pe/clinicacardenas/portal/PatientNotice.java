package pe.clinicacardenas.portal;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Aviso en la bandeja de la app del paciente; la app lo muestra como notificación del teléfono. */
@Entity
@Table(name = "patient_notices")
public class PatientNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    public String kind;
    public String title;
    public String body;
    @Column(name = "ref_id")
    public Long refId;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "read_at")
    public LocalDateTime readAt;
}
