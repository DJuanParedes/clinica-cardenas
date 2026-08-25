package pe.clinica.cardenas.operationsservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "COMPLAINTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 80)
    private String area;
    @Column(nullable = false, length = 500)
    private String description;
    private LocalDate createdAt;
    private LocalDate dueDate;
    @Column(length = 120)
    private String responsible;
    @Column(length = 500)
    private String response;
    @Column(nullable = false, length = 30)
    private String complaintStatus;
}
