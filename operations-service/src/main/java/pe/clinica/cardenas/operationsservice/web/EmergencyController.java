package pe.clinica.cardenas.operationsservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.operationsservice.domain.Emergency;
import pe.clinica.cardenas.operationsservice.domain.Status;
import pe.clinica.cardenas.operationsservice.repository.EmergencyRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/emergencies")
@RequiredArgsConstructor
public class EmergencyController {
    private final EmergencyRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public List<Emergency> list() { return repository.findAllByOrderByArrivalAtDesc(); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Emergency create(@RequestBody Emergency emergency) {
        emergency.setId(null);
        emergency.setArrivalAt(LocalDateTime.now());
        emergency.setStatus(Status.PENDIENTE);
        return repository.save(emergency);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Emergency update(@PathVariable Long id, @RequestBody Emergency incoming) {
        Emergency current = repository.findById(id).orElseThrow();
        current.setPatientId(incoming.getPatientId());
        current.setPatientName(incoming.getPatientName());
        current.setPriority(incoming.getPriority());
        current.setEmergencyType(incoming.getEmergencyType());
        current.setPainLevel(incoming.getPainLevel());
        current.setSymptoms(incoming.getSymptoms());
        return repository.save(current);
    }

    @PatchMapping("/{id}/attend")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Emergency attend(@PathVariable Long id, @RequestParam String doctor) {
        Emergency emergency = repository.findById(id).orElseThrow();
        emergency.setAssignedDoctor(doctor);
        emergency.setStatus(Status.EN_PROCESO);
        return repository.save(emergency);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Emergency status(@PathVariable Long id, @RequestParam Status value) {
        Emergency emergency = repository.findById(id).orElseThrow();
        emergency.setStatus(value);
        return repository.save(emergency);
    }
}
