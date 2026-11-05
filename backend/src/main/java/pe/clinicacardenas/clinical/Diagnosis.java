package pe.clinicacardenas.clinical;

import jakarta.persistence.*;
import pe.clinicacardenas.agenda.Dentist;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagnoses")
public class Diagnosis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;
    @Column(name = "cie10_code")
    public String cie10Code;
    public String description;
    public Integer tooth;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
