package pe.clinica.cardenas.appointmentservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "APPOINTMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Long patientId;

    @NotBlank @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String patientName;

    @NotBlank @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String doctorName;

    @Column(length = 160)
    private String doctorAccountEmail;

    @NotBlank @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String specialty;

    @NotBlank @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String chair;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime startAt;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(length = 300)
    private String reason;

    private Long treatmentId;

    @Column(length = 120)
    private String treatmentName;

    private Integer durationMinutes;

    private Integer cleaningBufferMinutes;

    /** Proforma aceptada a la que corresponde esta cita (si aplica). */
    private Long proformaId;

    @Column(length = 30)
    private String proformaCode;

    /** Referencia estable de la línea/tratamiento dentro de la proforma. */
    @Column(length = 64)
    private String proformaLineReference;

    @Column(length = 500)
    private String validationNotes;

    /** Resultado real del control de asistencia. Se conserva aunque la cita luego se cierre o reprograme. */
    @Column(length = 30)
    private String attendanceOutcome;

    /** Resolución posterior a una inasistencia o reprogramación. */
    @Column(length = 50)
    private String resolution;

    /** Enlaces de trazabilidad entre la cita histórica y la cita creada por reprogramación. */
    private Long reprogrammedFromAppointmentId;
    private Long reprogrammedToAppointmentId;

    @Enumerated(EnumType.STRING)
    private Status status;
}
