package pe.clinica.cardenas.financeservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PAYMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long budgetId;
    private Long encounterId;
    private Long appointmentId;
    private Long proformaId;
    @Column(length = 30)
    private String proformaCode;
    @Column(length = 64)
    private String proformaLineReference;
    @Column(length = 180)
    private String treatmentName;
    private Long patientId;
    @NotBlank @Column(nullable = false, length = 120)
    private String patientName;
    @Column(length = 180)
    private String concept;
    @NotNull @Positive @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @NotBlank @Column(nullable = false, length = 30)
    private String paymentMethod;
    @Column(length = 100)
    private String reference;
    @Column(nullable = false, length = 30)
    private String paymentStatus;
    @Column(nullable = false, length = 120)
    private String registeredBy;
    @Column(nullable = false)
    private LocalDateTime paidAt;
}
