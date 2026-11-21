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

    private final pe.clinicacardenas.crm.PatientInsightsService insights;

    public PatientController(PatientService service, AppointmentRepository appointments, AuditService audit,
                             pe.clinicacardenas.crm.PatientInsightsService insights) {
        this.insights = insights;
        this.service = service;
        this.appointments = appointments;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(defaultValue = "false") boolean mine,
                                      @RequestParam(required = false) String tag,
                                      @RequestParam(required = false) String source,
                                      @RequestParam(required = false) String task,
                                      @RequestParam(required = false) String next,
                                      @RequestParam(required = false) String budget,
                                      @RequestParam(required = false) Boolean debt,
                                      @RequestParam(required = false) String visit) {
        var extra = new pe.clinicacardenas.crm.SegmentFilter(null, null, null, null, null, null, visit, next, budget, debt, task, null, null, null).clean();
        Map<String, Object> r = service.search(q, page, size, mine, tag, source, extra);
        // v2.7: última y próxima cita, tarea pendiente, avance del presupuesto y saldo de cada paciente de la página
        List<Long> ids = ((List<?>) r.get("content")).stream().map(x -> ((Patient) x).id).toList();
        r.put("insights", insights.forPatients(ids));
        if (!fullAccess()) {
            r.put("content", ((List<?>) r.get("content")).stream().map(x -> reduced((Patient) x)).toList());
            r.put("restricted", true);
        }
        return r;
    }

    /**
     * Minimización de datos (Ley 29733, art. 8 y Reglamento): quien solo cobra (caja) ve lo necesario para cobrar y
     * emitir el comprobante: nombre, documento, correo y un teléfono enmascarado; no la dirección, fecha de nacimiento,
     * contactos ni datos clínicos.
     */
    static boolean fullAccess() {
        return pe.clinicacardenas.security.PermissionService.has("PACIENTES_EDITAR") || pe.clinicacardenas.security.PermissionService.has("HISTORIA_CLINICA");
    }

    static Map<String, Object> reduced(Patient p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", p.id);
        m.put("firstName", p.firstName);
        m.put("lastName", p.lastName);
        m.put("fullName", p.getFullName());
        m.put("docType", p.docType);
        m.put("docNumber", p.docNumber);
        m.put("email", p.email);
        m.put("phone", p.phone == null || p.phone.length() < 4 ? null : "*****" + p.phone.substring(p.phone.length() - 4));
        m.put("minor", p.isMinor());
        if (p.isMinor()) m.put("guardianName", p.guardianName);
        // Caja emite la factura: necesita el RUC y la razón social, no el resto de la ficha.
        m.put("fiscalRuc", p.fiscalRuc);
        m.put("fiscalName", p.fiscalName);
        m.put("fiscalAddress", p.fiscalAddress);
        m.put("hcNumber", p.hcNumber);
        m.put("restricted", true);
        return m;
    }

    @GetMapping("/check-document")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> check(@RequestParam String type, @RequestParam String number,
                                     @RequestParam(required = false) Long excludeId) {
        return service.checkDocument(type, number, excludeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> get(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        Map<String, Object> m = service.summary(id);
        boolean clinical = auth != null && auth.getAuthorities().stream().anyMatch(a -> "HISTORIA_CLINICA".equals(a.getAuthority()));
        if (!clinical) {
            m.remove("clinicalAlerts");      // datos de salud: solo el personal asistencial (Ley 29733, datos sensibles)
            m.remove("anamnesisAlerts");
            m.remove("pendingProcedures");
        }
        if (!fullAccess()) m.put("patient", reduced((Patient) m.get("patient")));
        return m;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Patient create(@RequestBody PatientValidator.Input in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Patient update(@PathVariable Long id, @RequestBody PatientValidator.Input in) {
        return service.update(id, in);
    }

    @GetMapping("/{id}/appointments")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public List<Appointment> appointments(@PathVariable Long id) {
        service.get(id);
        return appointments.findByPatientIdOrderByStartAtDesc(id);
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public List<AuditLog> audit(@PathVariable Long id) {
        return audit.forEntity("PACIENTE", id);
    }
}
