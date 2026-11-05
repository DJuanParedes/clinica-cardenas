package pe.clinicacardenas.patient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentRepository;
import pe.clinicacardenas.common.AuditLog;
import pe.clinicacardenas.common.AuditService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;
    private final AppointmentRepository appointments;
    private final AuditService audit;

    public PatientController(PatientService service, AppointmentRepository appointments, AuditService audit) {
        this.service = service;
        this.appointments = appointments;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/check-document")
    public Map<String, Object> check(@RequestParam String type, @RequestParam String number,
                                     @RequestParam(required = false) Long excludeId) {
        return service.checkDocument(type, number, excludeId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.summary(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Patient create(@RequestBody PatientValidator.Input in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Patient update(@PathVariable Long id, @RequestBody PatientValidator.Input in) {
        return service.update(id, in);
    }

    @GetMapping("/{id}/appointments")
    public List<Appointment> appointments(@PathVariable Long id) {
        service.get(id);
        return appointments.findByPatientIdOrderByStartAtDesc(id);
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public List<AuditLog> audit(@PathVariable Long id) {
        return audit.forEntity("PACIENTE", id);
    }
}
