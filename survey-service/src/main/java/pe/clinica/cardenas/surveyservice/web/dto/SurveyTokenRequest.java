package pe.clinica.cardenas.surveyservice.web.dto;

/**
 * Datos permitidos para generar un código de encuesta vinculado a una atención.
 * El código, vencimiento y estado de uso se generan en el servidor.
 */
public record SurveyTokenRequest(
        Long encounterId,
        Long patientId,
        String doctorName,
        String specialty,
        String branch,
        String chairName
) {
}
