package pe.clinica.cardenas.patientservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "PATIENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String fullName;

    @Pattern(regexp = "\\d{8}")
    @Column(nullable = false, unique = true, length = 8)
    private String dni;

    private LocalDate birthDate;

    @Column(length = 80)
    private String district;

    @Column(length = 180)
    private String address;

    @Pattern(regexp = "^$|\\d{9}")
    @Column(length = 9)
    private String phone;

    @Email
    @Column(length = 160)
    private String email;

    @Column(length = 120)
    private String occupation;

    @Column(length = 120)
    private String emergencyContact;

    @Pattern(regexp = "^$|\\d{9}")
    @Column(length = 9)
    private String emergencyPhone;

    @Column(length = 500)
    private String currentCondition;

    @Column(length = 500)
    private String familyHistory;

    @Column(length = 300)
    private String allergies;

    @Enumerated(EnumType.STRING)
    private Status status;
}
