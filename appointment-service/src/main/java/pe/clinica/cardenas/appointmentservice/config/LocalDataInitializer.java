package pe.clinica.cardenas.appointmentservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.appointmentservice.domain.*;
import pe.clinica.cardenas.appointmentservice.repository.AppointmentRepository;
import pe.clinica.cardenas.appointmentservice.repository.DentalChairRepository;
import pe.clinica.cardenas.appointmentservice.repository.DoctorProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final AppointmentRepository appointments;
    private final DoctorProfileRepository doctors;
    private final DentalChairRepository chairs;

    @Bean
    CommandLineRunner seedAppointments() {
        return args -> {
            ensureDoctor("Dra. Valeria Cárdenas", "doctor@cardenas.pe", "Rehabilitación oral", LocalTime.of(8, 0), LocalTime.of(17, 0));
            ensureDoctor("Dr. Diego Salazar", null, "Endodoncia", LocalTime.of(9, 0), LocalTime.of(18, 0));
            ensureDoctor("Dra. Ana Medina", null, "Odontopediatría", LocalTime.of(8, 0), LocalTime.of(14, 0));

            ensureChair("Sillón 1", "Consultorio general");
            ensureChair("Sillón 2", "Endodoncia y cirugía");
            ensureChair("Sillón 3", "Odontopediatría");
            if (appointments.count() == 0) {
                var today = LocalDate.now();
                appointments.save(Appointment.builder().patientId(1L).patientName("Andrea Flores Mendoza").doctorName("Dra. Valeria Cárdenas").doctorAccountEmail("doctor@cardenas.pe").specialty("Rehabilitación oral").chair("Sillón 1").startAt(today.atTime(9,0)).endAt(today.atTime(10,0)).reason("Evaluación de corona").status(Status.PENDIENTE).build());
                appointments.save(Appointment.builder().patientId(2L).patientName("José Quispe Ramos").doctorName("Dr. Diego Salazar").specialty("Endodoncia").chair("Sillón 2").startAt(today.atTime(10,30)).endAt(today.atTime(12,0)).reason("Tratamiento de conducto").status(Status.PENDIENTE).build());
                appointments.save(Appointment.builder().patientId(3L).patientName("Sofía Torres Díaz").doctorName("Dra. Ana Medina").specialty("Odontopediatría").chair("Sillón 3").startAt(today.plusDays(1).atTime(13,0)).endAt(today.plusDays(1).atTime(13,45)).reason("Control preventivo").status(Status.PENDIENTE).build());
            }
        };
    }

    private void ensureDoctor(String fullName, String accountEmail, String specialty, LocalTime shiftStart, LocalTime shiftEnd) {
        var existing = doctors.findByFullNameIgnoreCase(fullName);
        if (existing.isEmpty()) {
            doctors.save(DoctorProfile.builder()
                    .fullName(fullName)
                    .accountEmail(normalizeEmail(accountEmail))
                    .specialty(specialty)
                    .branch("Sede principal")
                    .shiftStart(shiftStart)
                    .shiftEnd(shiftEnd)
                    .active(true)
                    .build());
            return;
        }
        DoctorProfile doctor = existing.get();
        if ((doctor.getAccountEmail() == null || doctor.getAccountEmail().isBlank()) && accountEmail != null && !accountEmail.isBlank()) {
            doctor.setAccountEmail(normalizeEmail(accountEmail));
            doctors.save(doctor);
        }
    }

    private void ensureChair(String name, String description) {
        if (chairs.findByNameIgnoreCase(name).isEmpty()) {
            chairs.save(DentalChair.builder()
                    .name(name)
                    .branch("Sede principal")
                    .description(description)
                    .active(true)
                    .operationalStatus("DISPONIBLE")
                    .build());
        }
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
