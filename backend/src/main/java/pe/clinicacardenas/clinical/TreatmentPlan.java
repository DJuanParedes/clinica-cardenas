package pe.clinicacardenas.clinical;

import jakarta.persistence.*;
import pe.clinicacardenas.agenda.Dentist;

import java.time.LocalDateTime;

@Entity
@Table(name = "treatment_plans")
public class TreatmentPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;
    public String title;
    public String notes;
    /** ACTIVO, COMPLETADO, ANULADO */
    public String status;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
