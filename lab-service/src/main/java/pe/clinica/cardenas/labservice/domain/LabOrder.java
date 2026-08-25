package pe.clinica.cardenas.labservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "LAB_ORDERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LabOrder extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String patientName;
    @Column(nullable = false, length = 120)
    private String doctorName;
    @Column(nullable = false, length = 120)
    private String laboratory;
    @Column(nullable = false, length = 100)
    private String workType;
    @Column(length = 80)
    private String toothPieces;
    @Column(length = 80)
    private String material;
    @Column(length = 40)
    private String shade;
    private LocalDate sentDate;
    private LocalDate expectedDate;
    private LocalDate receivedDate;
    private BigDecimal cost;
    @Column(length = 400)
    private String notes;
    @Column(nullable = false, length = 50)
    private String labStatus;
}
