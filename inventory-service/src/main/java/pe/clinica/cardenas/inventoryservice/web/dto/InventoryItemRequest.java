package pe.clinica.cardenas.inventoryservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryItemRequest(
        @NotBlank String code,
        @NotBlank String name,
        String category,
        @NotBlank String unit,
        @NotNull @PositiveOrZero BigDecimal currentStock,
        @NotNull @PositiveOrZero BigDecimal minimumStock,
        String lot,
        LocalDate expiryDate,
        @PositiveOrZero BigDecimal unitCost,
        String inventoryStatus,
        Boolean active
) {}
