package pe.clinica.cardenas.appointmentservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DENTAL_CHAIRS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DentalChair extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String name;
    @Column(nullable = false, length = 80)
    private String branch;
    @Column(length = 180)
    private String description;
    @Column(nullable = false)
    private boolean active;
    @Column(length = 30)
    private String operationalStatus;
    private java.time.LocalDateTime unavailableUntil;
}
