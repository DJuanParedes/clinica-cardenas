package pe.clinica.cardenas.encounterservice.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TreatmentCatalogRequest(
        @NotBlank String name,
        String category,
        String specialty,
        @NotNull @Min(10) @Max(480) Integer defaultDurationMinutes,
        @DecimalMin("0.00") BigDecimal referencePrice
) {}
