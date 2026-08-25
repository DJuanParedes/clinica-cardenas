package pe.clinica.cardenas.encounterservice.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SavedFilterRequest(
        @NotBlank String name,
        @NotBlank String reportType,
        @NotBlank String parametersJson,
        Boolean shared
) {}
