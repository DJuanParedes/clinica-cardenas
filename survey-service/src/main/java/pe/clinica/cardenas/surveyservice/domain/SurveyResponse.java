package pe.clinica.cardenas.surveyservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "SURVEY_RESPONSES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SurveyResponse extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 24)
    private String surveyCode;
    private Long encounterId;
    @Column(length = 120) private String doctorName;
    @Column(length = 80) private String specialty;
    @Column(length = 80) private String branch;
    @Column(length = 60) private String chairName;

    @NotNull @Min(1) @Max(5) private Integer receptionScore;
    @NotNull @Min(1) @Max(5) private Integer kindnessScore;
    @NotNull @Min(1) @Max(5) private Integer waitTimeScore;
    @NotNull @Min(1) @Max(5) private Integer explanationScore;
    @NotNull @Min(1) @Max(5) private Integer doctorTreatmentScore;
    @NotNull @Min(1) @Max(5) private Integer cleanlinessScore;
    @NotNull @Min(1) @Max(5) private Integer comfortScore;
    @NotNull @Min(1) @Max(5) private Integer priceClarityScore;
    @NotNull @Min(1) @Max(5) private Integer overallScore;
    @NotNull private Boolean recommend;

    @Column(length = 1000) private String likedMost;
    @Column(length = 1000) private String improvement;
    @Column(length = 1000) private String additionalComment;
    private Boolean contactRequested;
    @Column(length = 120) private String contactName;
    @Pattern(regexp = "^$|^[0-9]{9}$", message = "debe tener 9 dígitos")
    @Column(length = 20) private String contactPhone;
    @Email @Column(length = 160) private String contactEmail;
    @Column(nullable = false) private boolean anonymous;
    @Column(nullable = false) private LocalDateTime submittedAt;
    @Column(nullable = false) private boolean requiresAttention;
    @Column(length = 30) private String followUpStatus;
}
