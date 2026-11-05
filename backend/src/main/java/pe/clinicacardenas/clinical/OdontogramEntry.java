package pe.clinicacardenas.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "odontogram_entries")
public class OdontogramEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    public int tooth;
    /** V vestibular, L lingual/palatino, M mesial, D distal, O oclusal/incisal, X pieza completa. */
    public String face;
    @Column(name = "condition_code")
    @JsonProperty("condition")
    public String conditionCode;
    public String note;
    @Column(name = "recorded_at")
    public LocalDateTime recordedAt;
    @Column(name = "recorded_by")
    public String recordedBy;
}
