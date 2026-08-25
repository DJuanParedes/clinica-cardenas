package pe.clinica.cardenas.encounterservice.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record BackupVerificationRequest(
        @NotBlank String backupName,
        @NotBlank String scope,
        @NotBlank String location,
        Long sizeMb,
        String checksum,
        @NotBlank String status,
        Boolean encrypted,
        Boolean offsiteCopy,
        LocalDateTime completedAt,
        LocalDateTime restorationTestedAt,
        String notes
) {}
