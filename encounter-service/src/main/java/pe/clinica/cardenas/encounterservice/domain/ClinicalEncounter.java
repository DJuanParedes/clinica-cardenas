package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="CLINICAL_ENCOUNTERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClinicalEncounter extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=30) private String code;
 @NotNull @Column(nullable=false) private Long appointmentId;
 @NotNull @Column(nullable=false) private Long patientId;
 @NotBlank @Column(nullable=false,length=120) private String patientName;
 private Long doctorId;
 @NotBlank @Column(nullable=false,length=120) private String doctorName;
 @Column(length=160) private String doctorAccountEmail;
 private Long chairId;
 @NotBlank @Column(nullable=false,length=60) private String chairName;
 @Column(length=80) private String specialty;
 @Column(nullable=false) private LocalDateTime scheduledStart;
 @Column(nullable=false) private LocalDateTime scheduledEnd;
 private LocalDateTime arrivedAt;
 private LocalDateTime actualStart;
 private LocalDateTime actualEnd;
 @Column(nullable=false,length=30) private String status;
 @Column(length=500) private String treatmentNames;
 private Integer durationMinutes;
 private Long proformaId;
 @Column(length=30) private String proformaCode;
 @Column(length=64) private String proformaLineReference;
 /** Valor acordado para el tratamiento de esta cita según la proforma; producción solo se reconoce al finalizar. */
 @Column(precision=14,scale=2) private BigDecimal plannedTreatmentAmount;
 @Column(precision=14,scale=2) private BigDecimal productionAmount;
 @Column(precision=14,scale=2) private BigDecimal paidAmount;
 @Column(precision=14,scale=2) private BigDecimal visitDayPaidAmount;
 @Column(nullable=false,length=30) private String paymentStatus;
 private Long budgetId;
 @Column(length=1500) private String notes;
 private Boolean labRepetition;
 private Boolean correctiveTreatment;
 private Boolean postTreatmentEmergency;
 private Boolean followUpCompleted;
 private Integer satisfactionScore;
 @Column(nullable=false) private LocalDateTime createdAt;
 @Column(nullable=false) private LocalDateTime updatedAt;
}
