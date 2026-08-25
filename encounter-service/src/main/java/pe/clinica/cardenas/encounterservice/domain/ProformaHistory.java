package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="TREATMENT_PROFORMA_HISTORY")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProformaHistory extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Long proformaId;

    @Column(nullable=false, length=40)
    private String action;

    @Column(length=1000)
    private String detail;

    @Column(nullable=false, length=160)
    private String actor;

    @Column(nullable=false)
    private LocalDateTime createdAt;
}
