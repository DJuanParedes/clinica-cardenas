package pe.clinica.cardenas.surveyservice.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Datos que el portal público puede enviar al registrar una encuesta.
 * Los campos internos de auditoría y seguimiento se calculan exclusivamente
 * en el servidor y no forman parte de este contrato.
 */
public record SurveySubmissionRequest(
        String surveyCode,
        @NotNull @Min(1) @Max(5) Integer receptionScore,
        @NotNull @Min(1) @Max(5) Integer kindnessScore,
        @NotNull @Min(1) @Max(5) Integer waitTimeScore,
        @NotNull @Min(1) @Max(5) Integer explanationScore,
        @NotNull @Min(1) @Max(5) Integer doctorTreatmentScore,
        @NotNull @Min(1) @Max(5) Integer cleanlinessScore,
        @NotNull @Min(1) @Max(5) Integer comfortScore,
        @NotNull @Min(1) @Max(5) Integer priceClarityScore,
        @NotNull @Min(1) @Max(5) Integer overallScore,
        @NotNull Boolean recommend,
        String likedMost,
        String improvement,
        String additionalComment,
        Boolean contactRequested,
        String contactName,
        @Pattern(regexp = "^$|^[0-9]{9}$", message = "debe tener 9 dígitos") String contactPhone,
        @Email String contactEmail,
        Boolean anonymous
) {
}
