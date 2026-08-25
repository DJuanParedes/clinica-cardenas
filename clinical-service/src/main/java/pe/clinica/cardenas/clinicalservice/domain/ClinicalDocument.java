package pe.clinica.cardenas.clinicalservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CLINICAL_DOCUMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClinicalDocument extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(nullable = false, length = 180)
    private String fileName;
    @Column(nullable = false, length = 40)
    private String version;
    @Column(nullable = false, length = 120)
    private String uploadedBy;
    @Column(length = 120)
    private String storageKey;
    @Column(length = 180)
    private String contentType;
    private Long fileSize;
    @Column(length = 500)
    private String notes;
    private LocalDateTime uploadedAt;
    @Column(nullable = false, length = 30)
    private String documentStatus;
}
