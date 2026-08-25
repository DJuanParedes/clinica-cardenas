package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "EMPLOYEES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String fullName;
    @Column(nullable = false, length = 80)
    private String position;
    @Column(nullable = false, length = 80)
    private String area;
    @Column(nullable = false, length = 80)
    private String branch;
    @Column(length = 80)
    private String shift;
    private LocalDate contractEnd;
    @Column(nullable = false, length = 30)
    private String employeeStatus;
}
