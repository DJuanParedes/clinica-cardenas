package pe.clinica.cardenas.appointmentservice.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.appointmentservice.domain.*;
import pe.clinica.cardenas.appointmentservice.repository.*;
import pe.clinica.cardenas.appointmentservice.service.AuditClient;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentRepository repository;
    private final DoctorProfileRepository doctors;
    private final DentalChairRepository chairs;
    private final AuditClient audit;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public List<Appointment> list(Authentication authentication) {
        List<Appointment> all = repository.findAllByOrderByStartAtAsc();
        boolean doctor = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        if (!doctor) return all;
        return all.stream().filter(item -> belongsToDoctor(item, authentication)).toList();
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public List<Appointment> analytics(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser anterior a la inicial");
        return repository.findAllByOrderByStartAtAsc().stream()
                .filter(a -> a.getStartAt() != null && !a.getStartAt().toLocalDate().isBefore(from) && !a.getStartAt().toLocalDate().isAfter(to))
                .toList();
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<Appointment> patientHistory(@PathVariable Long patientId, Authentication authentication) {
        List<Appointment> rows = repository.findAllByPatientIdOrderByStartAtDesc(patientId);
        boolean doctor = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        return doctor ? rows.stream().filter(item -> belongsToDoctor(item, authentication)).toList() : rows;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Appointment get(@PathVariable Long id, Authentication authentication) {
        Appointment appointment = find(id);
        ensureDoctorOwnership(appointment, authentication);
        return appointment;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    @Transactional
    public Appointment create(@Valid @RequestBody Appointment appointment, HttpServletRequest request) {
        appointment.setId(null);
        appointment.setStatus(Status.PENDIENTE);
        appointment.setAttendanceOutcome(null);
        appointment.setResolution(null);
        appointment.setReprogrammedFromAppointmentId(null);
        appointment.setReprogrammedToAppointmentId(null);
        audit.validateProformaLink(appointment.getProformaId(), appointment.getPatientId(), appointment.getProformaLineReference(), null, request.getHeader("Authorization"));
        validate(appointment, null);
        Appointment saved = repository.save(appointment);
        audit.log(saved.getId().toString(), "CREAR", null, saved, "Nueva cita", request.getHeader("Authorization"));
        audit.ensureEncounter(saved, request.getHeader("Authorization"));
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    @Transactional
    public Appointment update(@PathVariable Long id, @Valid @RequestBody Appointment incoming, HttpServletRequest request) {
        Appointment current = find(id);
        if (java.util.Set.of(Status.LLEGO, Status.EN_PROCESO, Status.COMPLETADO, Status.CANCELADO, Status.NO_ASISTIO, Status.REPROGRAMADA).contains(current.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya pasó por control de asistencia y no puede editarse. Usa el flujo de estado correspondiente.");
        }
        boolean schedulingChanged = !java.util.Objects.equals(current.getDoctorName(), incoming.getDoctorName())
                || !java.util.Objects.equals(current.getChair(), incoming.getChair())
                || !java.util.Objects.equals(current.getStartAt(), incoming.getStartAt())
                || !java.util.Objects.equals(current.getEndAt(), incoming.getEndAt());
        if (schedulingChanged) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Para cambiar doctor, sillón u horario usa Reprogramar. Así se conserva el historial de la cita anterior.");
        }
        String before = current.toString();
        audit.validateProformaLink(incoming.getProformaId(), incoming.getPatientId(), incoming.getProformaLineReference(), id, request.getHeader("Authorization"));
        validate(incoming, id);
        current.setPatientId(incoming.getPatientId());
        current.setPatientName(incoming.getPatientName());
        current.setDoctorName(incoming.getDoctorName());
        current.setDoctorAccountEmail(incoming.getDoctorAccountEmail());
        current.setSpecialty(incoming.getSpecialty());
        current.setChair(incoming.getChair());
        current.setStartAt(incoming.getStartAt());
        current.setEndAt(incoming.getEndAt());
        current.setReason(incoming.getReason());
        current.setTreatmentId(incoming.getTreatmentId());
        current.setTreatmentName(incoming.getTreatmentName());
        current.setDurationMinutes(incoming.getDurationMinutes());
        current.setCleaningBufferMinutes(incoming.getCleaningBufferMinutes());
        current.setProformaId(incoming.getProformaId());
        current.setProformaCode(incoming.getProformaCode());
        current.setProformaLineReference(incoming.getProformaLineReference());
        current.setValidationNotes(incoming.getValidationNotes());
        Appointment saved = repository.save(current);
        audit.ensureEncounter(saved, request.getHeader("Authorization"));
        audit.log(id.toString(), "ACTUALIZAR", before, saved, "Actualización administrativa sin modificar horario ni recursos", request.getHeader("Authorization"));
        return saved;
    }

    @PostMapping("/{id}/reprogram")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    @Transactional
    public Appointment reprogram(@PathVariable Long id, @Valid @RequestBody Appointment incoming, HttpServletRequest request) {
        Appointment current = find(id);
        if ("LLEGO".equalsIgnoreCase(current.getAttendanceOutcome()) || java.util.Set.of(Status.LLEGO, Status.EN_PROCESO, Status.COMPLETADO, Status.CANCELADO, Status.REPROGRAMADA).contains(current.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El paciente ya llegó o la cita está cerrada; cancelar/reprogramar ya no es una acción válida");
        }
        // NO_ASISTIO sí puede resolverse mediante reprogramación. La nueva cita vuelve a PENDIENTE.
        if (incoming.getProformaId() == null && current.getProformaId() != null) {
            incoming.setProformaId(current.getProformaId());
            incoming.setProformaCode(current.getProformaCode());
            incoming.setProformaLineReference(current.getProformaLineReference());
        }
        if (incoming.getTreatmentId() == null && current.getTreatmentId() != null) {
            incoming.setTreatmentId(current.getTreatmentId());
            incoming.setTreatmentName(current.getTreatmentName());
        }
        audit.validateProformaLink(incoming.getProformaId(), incoming.getPatientId(), incoming.getProformaLineReference(), id, request.getHeader("Authorization"));
        validate(incoming, id);
        Status beforeStatus = current.getStatus();
        current.setStatus(Status.REPROGRAMADA);
        current.setResolution("REPROGRAMADA");
        Appointment historical = repository.save(current);

        incoming.setId(null);
        incoming.setStatus(Status.PENDIENTE);
        incoming.setAttendanceOutcome(null);
        incoming.setResolution(null);
        incoming.setReprogrammedFromAppointmentId(current.getId());
        incoming.setReprogrammedToAppointmentId(null);
        Appointment saved = repository.save(incoming);
        historical.setReprogrammedToAppointmentId(saved.getId());
        repository.save(historical);
        String authHeader = request.getHeader("Authorization");
        // La sincronización remota se ejecuta antes del commit local y usa el snapshot de la nueva cita.
        // Si la llamada remota falla, la transacción local no confirma la reprogramación.
        // Como son dos bases distintas, esto no constituye una transacción distribuida ACID;
        // el endpoint remoto es idempotente y la suite funcional comprueba el flujo de reconciliación.
        audit.reprogramEncounter(historical.getId(), saved, authHeader);
        afterCommit(() -> {
            audit.log(id.toString(), "REPROGRAMAR_ORIGEN", beforeStatus, Status.REPROGRAMADA, "Horario original liberado; se conserva historial", authHeader);
            audit.log(saved.getId().toString(), "REPROGRAMAR_DESTINO", null, saved, "Nueva cita generada desde reprogramación de la cita " + historical.getId(), authHeader);
        });
        return saved;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    @Transactional
    public Appointment changeStatus(@PathVariable Long id, @RequestParam Status value,
                                    @RequestParam(defaultValue = "AGENDA") String source,
                                    HttpServletRequest request, Authentication authentication) {
        Appointment appointment = find(id);
        ensureDoctorOwnership(appointment, authentication);
        boolean doctorRole = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        if (doctorRole && !"ENCOUNTER".equalsIgnoreCase(source) &&
                java.util.Set.of(Status.LLEGO, Status.NO_ASISTIO, Status.CANCELADO).contains(value)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La confirmación de asistencia corresponde a Recepción o Administración");
        }
        Status before = appointment.getStatus() == null ? Status.PENDIENTE : appointment.getStatus();
        if (before == value) return appointment;
        if (value == Status.REPROGRAMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Usa la acción Reprogramar para conservar el historial y crear el nuevo horario");
        }
        if (java.util.Set.of(Status.COMPLETADO, Status.CANCELADO, Status.REPROGRAMADA).contains(before)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya está cerrada y su estado no puede reabrirse");
        }
        java.util.Set<Status> allowed = switch (before) {
            case PENDIENTE -> java.util.Set.of(Status.LLEGO, Status.NO_ASISTIO, Status.OBSERVADO);
            case CONFIRMADO -> java.util.Set.of(Status.LLEGO, Status.NO_ASISTIO, Status.OBSERVADO);
            // Una vez confirmada la llegada, el único paso siguiente es iniciar la atención.
            case LLEGO -> java.util.Set.of(Status.EN_PROCESO);
            case EN_PROCESO -> java.util.Set.of(Status.COMPLETADO);
            // La inasistencia abre exactamente dos salidas: cerrar o usar /reprogram.
            case NO_ASISTIO -> java.util.Set.of(Status.CANCELADO);
            case OBSERVADO -> java.util.Set.of(Status.PENDIENTE, Status.LLEGO, Status.NO_ASISTIO);
            default -> java.util.Set.of();
        };
        if (!allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transición de estado no permitida: " + before + " → " + value);
        }
        if ("LLEGO".equalsIgnoreCase(appointment.getAttendanceOutcome()) && (value == Status.CANCELADO || value == Status.NO_ASISTIO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El paciente ya llegó; la cita no puede cancelarse ni convertirse en inasistencia");
        }
        appointment.setStatus(value);
        if (value == Status.LLEGO) { appointment.setAttendanceOutcome("LLEGO"); appointment.setResolution(null); }
        if (value == Status.NO_ASISTIO) { appointment.setAttendanceOutcome("NO_ASISTIO"); appointment.setResolution("PENDIENTE_DECISION"); }
        if (before == Status.NO_ASISTIO && value == Status.CANCELADO) { appointment.setResolution("CERRADA_SIN_REPROGRAMAR"); }
        Appointment saved = repository.save(appointment);
        audit.log(id.toString(), "CAMBIAR_ESTADO", before, value, "Cambio de estado de cita", request.getHeader("Authorization"));
        if (!"ENCOUNTER".equalsIgnoreCase(source)) {
            audit.syncStatus(id, value.name(), request.getHeader("Authorization"));
        }
        return saved;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private void ensureDoctorOwnership(Appointment appointment, Authentication authentication) {
        boolean doctor = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        if (doctor && !belongsToDoctor(appointment, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar tus propias citas");
        }
    }

    private boolean belongsToDoctor(Appointment appointment, Authentication authentication) {
        String accountEmail = normalizeEmail(appointment.getDoctorAccountEmail());
        if (accountEmail != null) {
            return accountEmail.equalsIgnoreCase(authentication.getName());
        }
        String fullName = authentication.getDetails() == null ? "" : authentication.getDetails().toString();
        return appointment.getDoctorName() != null && appointment.getDoctorName().equalsIgnoreCase(fullName);
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private Appointment find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
    }

    private void validate(Appointment appointment, Long ignoredId) {
        if (appointment.getPatientId() == null || appointment.getPatientName() == null || appointment.getPatientName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un paciente");
        }
        if (appointment.getTreatmentName() != null && !appointment.getTreatmentName().isBlank() && appointment.getProformaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Toda cita de tratamiento debe vincularse a una proforma aceptada y a su tratamiento. Para consulta/evaluación deja el tratamiento vacío.");
        }
        if (appointment.getProformaId() != null && (appointment.getProformaLineReference() == null || appointment.getProformaLineReference().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona el tratamiento de la proforma que corresponde a esta cita");
        }
        if (appointment.getProformaId() == null) {
            appointment.setProformaCode(null);
            appointment.setProformaLineReference(null);
        }
        if (appointment.getStartAt() == null || appointment.getEndAt() == null ||
                !appointment.getEndAt().isAfter(appointment.getStartAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora final debe ser posterior a la hora inicial");
        }
        if (!appointment.getStartAt().toLocalDate().equals(appointment.getEndAt().toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cita debe iniciar y terminar el mismo día");
        }

        DoctorProfile doctor = doctors.findByFullNameIgnoreCase(appointment.getDoctorName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El doctor seleccionado no existe"));
        if (!doctor.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El doctor seleccionado está inactivo");
        appointment.setDoctorAccountEmail(normalizeEmail(doctor.getAccountEmail()));
        if (appointment.getSpecialty() == null || !doctor.getSpecialty().equalsIgnoreCase(appointment.getSpecialty())) {
            appointment.setSpecialty(doctor.getSpecialty());
        }
        LocalTime start = appointment.getStartAt().toLocalTime();
        LocalTime end = appointment.getEndAt().toLocalTime();
        LocalTime businessStart = LocalTime.of(8, 0);
        LocalTime businessEnd = LocalTime.of(22, 0);
        if (start.isBefore(businessStart) || end.isAfter(businessEnd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La agenda solo admite citas entre 08:00 y 22:00");
        }
        if (doctor.getShiftStart() != null && start.isBefore(doctor.getShiftStart()) ||
                doctor.getShiftEnd() != null && end.isAfter(doctor.getShiftEnd())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita está fuera del horario del doctor");
        }

        DentalChair chair = chairs.findByNameIgnoreCase(appointment.getChair())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El sillón seleccionado no existe"));
        if (!chair.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El sillón seleccionado está inactivo");
        String chairStatus = chair.getOperationalStatus() == null ? "DISPONIBLE" : chair.getOperationalStatus();
        if (java.util.Set.of("MANTENIMIENTO", "FUERA_DE_SERVICIO", "BLOQUEADO").contains(chairStatus.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El sillón no está operativo: " + chairStatus);
        }
        if (chair.getUnavailableUntil() != null && appointment.getStartAt().isBefore(chair.getUnavailableUntil())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El sillón no estará disponible hasta " + chair.getUnavailableUntil());
        }
        if (!doctor.getBranch().equalsIgnoreCase(chair.getBranch())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El doctor y el sillón pertenecen a sedes diferentes");
        }

        int buffer = appointment.getCleaningBufferMinutes() == null ? 15 : Math.max(0, appointment.getCleaningBufferMinutes());
        LocalDateTime bufferedEnd = appointment.getEndAt().plusMinutes(buffer);
        Optional<Appointment> conflicting = repository.findAll().stream().filter(existing -> {
            if ((ignoredId != null && existing.getId().equals(ignoredId)) ||
                    java.util.Set.of(Status.CANCELADO, Status.NO_ASISTIO, Status.REPROGRAMADA).contains(existing.getStatus())) return false;
            boolean rawOverlap = appointment.getStartAt().isBefore(existing.getEndAt()) &&
                    appointment.getEndAt().isAfter(existing.getStartAt());
            boolean doctorOrPatientConflict = rawOverlap &&
                    (existing.getDoctorName().equalsIgnoreCase(appointment.getDoctorName()) ||
                            existing.getPatientId().equals(appointment.getPatientId()));
            int existingBuffer = existing.getCleaningBufferMinutes() == null ? 15 : Math.max(0, existing.getCleaningBufferMinutes());
            boolean chairConflict = existing.getChair().equalsIgnoreCase(appointment.getChair()) &&
                    appointment.getStartAt().isBefore(existing.getEndAt().plusMinutes(existingBuffer)) &&
                    bufferedEnd.isAfter(existing.getStartAt());
            return doctorOrPatientConflict || chairConflict;
        }).findFirst();
        if (conflicting.isPresent()) {
            Appointment c = conflicting.get();
            boolean rawOverlap = appointment.getStartAt().isBefore(c.getEndAt()) && appointment.getEndAt().isAfter(c.getStartAt());
            String resource = rawOverlap && c.getPatientId().equals(appointment.getPatientId()) ? "El paciente" :
                    rawOverlap && c.getDoctorName().equalsIgnoreCase(appointment.getDoctorName()) ? "El doctor" : "El sillón";
            String reason = "El sillón".equals(resource) ? " ya tiene una cita o tiempo de limpieza en ese horario" : " ya tiene una cita en ese horario";
            throw new ResponseStatusException(HttpStatus.CONFLICT, resource + reason);
        }
        int minutes = (int) java.time.Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes();
        if (appointment.getDurationMinutes() != null && Math.abs(minutes - appointment.getDurationMinutes()) > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora final no coincide con la duración del tratamiento");
        }
        appointment.setDurationMinutes(minutes);
        appointment.setCleaningBufferMinutes(buffer);
    }
}
