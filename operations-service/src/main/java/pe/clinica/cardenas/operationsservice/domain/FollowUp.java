package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "FOLLOW_UPS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FollowUp extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 140)
    private String treatment;
    @Column(nullable = false, length = 120)
    private String doctorName;
    private LocalDate treatmentDate;
    private LocalDate followUpDate;
    @Column(length = 300)
    private String symptoms;
    @Column(length = 350)
    private String recommendations;
    @Column(nullable = false, length = 30)
    private String followUpStatus;
}
