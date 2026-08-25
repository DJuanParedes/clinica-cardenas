package pe.clinica.cardenas.appointmentservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record DentalChairRequest(
        @NotBlank String name,
        @NotBlank String branch,
        String description,
        Boolean active,
        String operationalStatus,
        LocalDateTime unavailableUntil
) {}
