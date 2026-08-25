package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DENTAL_EMERGENCIES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Emergency extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long patientId;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 30)
    private String priority;
    @Column(nullable = false, length = 100)
    private String emergencyType;
    private Integer painLevel;
    @Column(length = 400)
    private String symptoms;
    @Column(length = 120)
    private String assignedDoctor;
    private LocalDateTime arrivalAt;
    @Enumerated(EnumType.STRING)
    private Status status;
}
