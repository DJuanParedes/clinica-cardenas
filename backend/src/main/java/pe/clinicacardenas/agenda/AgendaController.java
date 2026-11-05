package pe.clinicacardenas.agenda;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.AuditLog;
import pe.clinicacardenas.common.AuditService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgendaController {

    private final AgendaService service;
    private final AuditService audit;

    public AgendaController(AgendaService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/dentists")
    public List<Dentist> dentists() {
        return service.activeDentists();
    }

    @GetMapping("/agenda/slots")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public Map<String, Object> slots(@RequestParam Long dentistId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "30") int duration,
                                     @RequestParam(required = false) Long excludeId) {
        return service.slots(dentistId, date, duration, excludeId);
    }

    @GetMapping("/agenda/day")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public Map<String, Object> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) Long dentistId) {
        return service.day(date, dentistId);
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public List<Appointment> range(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long dentistId) {
        return service.range(from, to, dentistId);
    }

    @GetMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public Appointment get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/appointments/{id}/audit")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public List<AuditLog> history(@PathVariable Long id) {
        return audit.forEntity("CITA", id);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public Appointment create(@RequestBody AgendaService.AppointmentRequest req) {
        return service.create(req);
    }

    @PutMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public Appointment reschedule(@PathVariable Long id, @RequestBody AgendaService.AppointmentRequest req) {
        return service.reschedule(id, req);
    }

    @PatchMapping("/appointments/{id}/status")
    @PreAuthorize("hasAuthority('CITAS_ESTADO')")
    public Appointment status(@PathVariable Long id, @RequestBody AgendaService.StatusRequest req) {
        return service.changeStatus(id, req);
    }
}
