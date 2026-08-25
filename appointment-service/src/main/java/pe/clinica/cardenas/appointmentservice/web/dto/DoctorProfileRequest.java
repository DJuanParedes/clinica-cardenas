package pe.clinica.cardenas.appointmentservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;

public record DoctorProfileRequest(
        @NotBlank String fullName,
        String accountEmail,
        @NotBlank String specialty,
        @NotBlank String branch,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        Boolean active
) {}
