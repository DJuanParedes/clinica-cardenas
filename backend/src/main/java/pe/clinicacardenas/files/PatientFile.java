package pe.clinicacardenas.files;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Foto intraoral, radiografía o documento de un paciente (el archivo vive en disco, aquí sus datos). */
@Entity
@Table(name = "patient_files")
public class PatientFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    public String kind;
    public String title;
    public String note;
    public Integer tooth;
    @Column(name = "content_type")
    public String contentType;
    @Column(name = "size_bytes")
    public long sizeBytes;
    @JsonIgnore
    @Column(name = "storage_name")
    public String storageName;
    @Column(name = "shared_with_patient")
    public boolean sharedWithPatient;
    @Column(name = "signed_at")
    public LocalDateTime signedAt;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
    @JsonIgnore
    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;
    @JsonIgnore
    @Column(name = "deleted_by")
    public String deletedBy;

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
