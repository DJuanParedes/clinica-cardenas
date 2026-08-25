package pe.clinica.cardenas.appointmentservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.appointmentservice.domain.DentalChair;
import pe.clinica.cardenas.appointmentservice.domain.DoctorProfile;
import pe.clinica.cardenas.appointmentservice.repository.DentalChairRepository;
import pe.clinica.cardenas.appointmentservice.repository.DoctorProfileRepository;
import pe.clinica.cardenas.appointmentservice.service.AuditClient;
import pe.clinica.cardenas.appointmentservice.web.dto.DentalChairRequest;
import pe.clinica.cardenas.appointmentservice.web.dto.DoctorProfileRequest;

import java.util.List;
import java.util.Set;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/scheduling")
@RequiredArgsConstructor
public class SchedulingResourceController {
    private static final Set<String> CHAIR_OPERATIONAL_STATUSES = Set.of("DISPONIBLE","MANTENIMIENTO","FUERA_DE_SERVICIO","BLOQUEADO");
    private final DoctorProfileRepository doctors;
    private final DentalChairRepository chairs;
    private final AuditClient audit;

    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public List<DoctorProfile> doctors() { return doctors.findAllByOrderByFullNameAsc(); }

    @PostMapping("/doctors")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorProfile createDoctor(@Valid @RequestBody DoctorProfileRequest body, HttpServletRequest request) {
        String accountEmail = normalizeEmail(body.accountEmail());
        ensureEmailAvailable(accountEmail, null);
        validateShift(body.shiftStart(), body.shiftEnd());
        DoctorProfile item = DoctorProfile.builder()
                .fullName(requireText(body.fullName(), "Debe indicar el nombre del doctor"))
                .accountEmail(accountEmail)
                .specialty(body.specialty().trim())
                .branch(body.branch().trim())
                .shiftStart(body.shiftStart())
                .shiftEnd(body.shiftEnd())
                .active(true)
                .build();
        DoctorProfile saved = doctors.save(item);
        audit.log("AGENDA", "DoctorProfile", saved.getId().toString(), "CREAR", null, saved, "Alta de doctor", request.getHeader("Authorization"));
        return saved;
    }

    @PutMapping("/doctors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorProfile updateDoctor(@PathVariable Long id, @Valid @RequestBody DoctorProfileRequest incoming, HttpServletRequest request) {
        DoctorProfile current = doctors.findById(id).orElseThrow(() -> notFound("Doctor no encontrado"));
        String before = current.toString();
        validateShift(incoming.shiftStart(), incoming.shiftEnd());
        current.setFullName(requireText(incoming.fullName(), "Debe indicar el nombre del doctor"));
        String accountEmail = normalizeEmail(incoming.accountEmail());
        ensureEmailAvailable(accountEmail, id);
        current.setAccountEmail(accountEmail);
        current.setSpecialty(incoming.specialty().trim());
        current.setBranch(incoming.branch().trim());
        current.setShiftStart(incoming.shiftStart());
        current.setShiftEnd(incoming.shiftEnd());
        if (incoming.active() != null) current.setActive(incoming.active());
        DoctorProfile saved = doctors.save(current);
        audit.log("AGENDA", "DoctorProfile", id.toString(), "ACTUALIZAR", before, saved, "Actualización de doctor", request.getHeader("Authorization"));
        return saved;
    }

    @PatchMapping("/doctors/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorProfile activeDoctor(@PathVariable Long id, @RequestParam boolean value, HttpServletRequest request) {
        DoctorProfile current = doctors.findById(id).orElseThrow(() -> notFound("Doctor no encontrado"));
        boolean before = current.isActive();
        current.setActive(value);
        DoctorProfile saved = doctors.save(current);
        audit.log("AGENDA", "DoctorProfile", id.toString(), "CAMBIAR_ESTADO", before, value, "Activación de doctor", request.getHeader("Authorization"));
        return saved;
    }

    @GetMapping("/chairs")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<DentalChair> chairs() { return chairs.findAllByOrderByNameAsc(); }

    @PostMapping("/chairs")
    @PreAuthorize("hasRole('ADMIN')")
    public DentalChair createChair(@Valid @RequestBody DentalChairRequest body, HttpServletRequest request) {
        DentalChair item = DentalChair.builder()
                .name(body.name().trim())
                .branch(body.branch().trim())
                .description(body.description())
                .active(true)
                .operationalStatus(validChairOperationalStatus(body.operationalStatus()))
                .unavailableUntil(body.unavailableUntil())
                .build();
        DentalChair saved = chairs.save(item);
        audit.log("AGENDA", "DentalChair", saved.getId().toString(), "CREAR", null, saved, "Alta de sillón", request.getHeader("Authorization"));
        return saved;
    }

    @PutMapping("/chairs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DentalChair updateChair(@PathVariable Long id, @Valid @RequestBody DentalChairRequest incoming, HttpServletRequest request) {
        DentalChair current = chairs.findById(id).orElseThrow(() -> notFound("Sillón no encontrado"));
        String before = current.toString();
        current.setName(incoming.name().trim());
        current.setBranch(incoming.branch().trim());
        current.setDescription(incoming.description());
        if (incoming.active() != null) current.setActive(incoming.active());
        current.setOperationalStatus(validChairOperationalStatus(incoming.operationalStatus()));
        current.setUnavailableUntil(incoming.unavailableUntil());
        DentalChair saved = chairs.save(current);
        audit.log("AGENDA", "DentalChair", id.toString(), "ACTUALIZAR", before, saved, "Cambio de estado o disponibilidad", request.getHeader("Authorization"));
        return saved;
    }

    @PatchMapping("/chairs/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public DentalChair activeChair(@PathVariable Long id, @RequestParam boolean value, HttpServletRequest request) {
        DentalChair current = chairs.findById(id).orElseThrow(() -> notFound("Sillón no encontrado"));
        boolean before = current.isActive();
        current.setActive(value);
        DentalChair saved = chairs.save(current);
        audit.log("AGENDA", "DentalChair", id.toString(), "CAMBIAR_ESTADO", before, value, "Activación del sillón", request.getHeader("Authorization"));
        return saved;
    }


    private String validChairOperationalStatus(String value) {
        String requested = value == null || value.isBlank() ? "DISPONIBLE" : value.trim();
        return CHAIR_OPERATIONAL_STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado operativo de sillón no permitido"));
    }

    private void ensureEmailAvailable(String email, Long ignoredId) {
        if (email == null) return;
        doctors.findByAccountEmailIgnoreCase(email)
                .filter(existing -> ignoredId == null || !existing.getId().equals(ignoredId))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está vinculado a otro doctor"); });
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }


    private void validateShift(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario del doctor no es válido");
        }
        if (start.isBefore(LocalTime.of(8, 0)) || end.isAfter(LocalTime.of(22, 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario del doctor debe estar entre 08:00 y 22:00");
        }
    }
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
