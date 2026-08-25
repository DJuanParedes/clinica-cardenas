package pe.clinica.cardenas.encounterservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChairGoalRequest(
        @NotBlank String chairName,
        @NotBlank String periodType,
        @NotNull LocalDate periodStart,
        BigDecimal productionGoal,
        BigDecimal collectionGoal,
        Integer utilizationGoal,
        Integer patientGoal
) {}
