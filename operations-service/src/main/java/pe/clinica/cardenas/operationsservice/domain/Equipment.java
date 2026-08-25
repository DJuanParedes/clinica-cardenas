package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "EQUIPMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Equipment extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 80)
    private String brand;
    @Column(length = 80)
    private String model;
    @Column(length = 80)
    private String serialNumber;
    @Column(nullable = false, length = 80)
    private String location;
    private LocalDate lastMaintenance;
    private LocalDate nextMaintenance;
    @Column(nullable = false, length = 30)
    private String equipmentStatus;
}
