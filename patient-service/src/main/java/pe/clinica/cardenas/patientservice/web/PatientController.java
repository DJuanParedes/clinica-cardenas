package pe.clinica.cardenas.patientservice.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.patientservice.domain.Patient;
import pe.clinica.cardenas.patientservice.domain.Status;
import pe.clinica.cardenas.patientservice.repository.PatientRepository;
import pe.clinica.cardenas.patientservice.service.AuditClient;

import java.util.Comparator;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientRepository repository;
    private final AuditClient audit;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public List<Patient> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Patient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<Patient> search(@RequestParam String term) {
        String value = term == null ? "" : term.trim();
        if (value.length() < 2) return List.of();
        String normalized = searchable(value);
        return repository.findAll().stream()
                .filter(p -> searchable(p.getFullName()).contains(normalized)
                        || searchable(p.getDni()).contains(normalized)
                        || searchable(p.getPhone()).contains(normalized)
                        || searchable(String.valueOf(p.getId())).contains(normalized))
                .sorted(Comparator.comparing(Patient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .limit(10).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Patient get(@PathVariable Long id) { return find(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Patient create(@Valid @RequestBody Patient patient, Authentication authentication, HttpServletRequest request) {
        normalize(patient);
        if (repository.existsByDni(patient.getDni())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese DNI");
        }
        patient.setId(null);
        patient.setStatus(Status.ACTIVO);
        normalize(patient);
        Patient saved = saveOrConflict(patient);
        audit.log(saved.getId().toString(), "CREAR", null, saved, "Registro de paciente", request.getHeader("Authorization"));
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Patient update(@PathVariable Long id, @Valid @RequestBody Patient incoming, Authentication authentication, HttpServletRequest request) {
        Patient current = find(id);
        String before = current.toString();
        normalize(incoming);
        repository.findByDni(incoming.getDni())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese DNI"); });
        normalize(incoming);
        current.setFullName(incoming.getFullName());
        current.setDni(incoming.getDni());
        current.setBirthDate(incoming.getBirthDate());
        current.setDistrict(incoming.getDistrict());
        current.setAddress(incoming.getAddress());
        current.setPhone(incoming.getPhone());
        current.setEmail(incoming.getEmail());
        current.setOccupation(incoming.getOccupation());
        current.setEmergencyContact(incoming.getEmergencyContact());
        current.setEmergencyPhone(incoming.getEmergencyPhone());
        current.setCurrentCondition(incoming.getCurrentCondition());
        current.setFamilyHistory(incoming.getFamilyHistory());
        current.setAllergies(incoming.getAllergies());
        Patient saved = saveOrConflict(current);
        audit.log(id.toString(), "ACTUALIZAR", before, saved, "Actualización administrativa", request.getHeader("Authorization"));
        return saved;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Patient status(@PathVariable Long id, @RequestParam Status value, HttpServletRequest request) {
        Patient current = find(id);
        Status before = current.getStatus();
        current.setStatus(value);
        Patient saved = saveOrConflict(current);
        audit.log(id.toString(), "CAMBIAR_ESTADO", before, value, "Cambio de estado del paciente", request.getHeader("Authorization"));
        return saved;
    }

    private Patient saveOrConflict(Patient patient) {
        try {
            return repository.saveAndFlush(patient);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Includes a concurrent insert with the same document. No DB details are exposed.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se pudo guardar el paciente. Verifica coincidencias de DNI y los datos ingresados.");
        }
    }

    private Patient find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
    }

    private String searchable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    private void normalize(Patient patient) {
        patient.setFullName(patient.getFullName().trim());
        patient.setDni(patient.getDni().trim());
        if (patient.getPhone() != null) patient.setPhone(patient.getPhone().trim());
        if (patient.getEmail() != null) patient.setEmail(patient.getEmail().trim().toLowerCase());
    }
}

