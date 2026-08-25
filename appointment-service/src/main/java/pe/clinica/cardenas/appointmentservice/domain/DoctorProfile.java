package pe.clinica.cardenas.appointmentservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "DOCTOR_PROFILES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorProfile extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 120)
    private String fullName;
    @Column(length = 160)
    private String accountEmail;
    @Column(nullable = false, length = 100)
    private String specialty;
    @Column(nullable = false, length = 80)
    private String branch;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    @Column(nullable = false)
    private boolean active;
}
