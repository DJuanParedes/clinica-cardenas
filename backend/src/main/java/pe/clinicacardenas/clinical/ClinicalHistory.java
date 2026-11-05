package pe.clinicacardenas.clinical;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_histories")
public class ClinicalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "blood_type")
    public String bloodType;
    public String allergies;
    @Column(name = "medical_background")
    public String medicalBackground;
    @Column(name = "current_medication")
    public String currentMedication;
    public String surgeries;
    public String habits;
    @Column(name = "family_background")
    public String familyBackground;
    public String observations;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    @Column(name = "updated_by")
    public String updatedBy;
}
