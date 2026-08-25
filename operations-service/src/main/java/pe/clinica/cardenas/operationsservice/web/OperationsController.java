package pe.clinica.cardenas.operationsservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.operationsservice.domain.*;
import pe.clinica.cardenas.operationsservice.repository.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {
    private static final Set<String> FOLLOW_UP_STATES = Set.of("PENDIENTE","CONTACTADO","REQUIERE EVALUACIÓN","CITA PROGRAMADA","FINALIZADO");
    private static final Set<String> COMPLAINT_STATES = Set.of("REGISTRADO","EN PROCESO","RESPONDIDO","CERRADO");
    private static final Set<String> EMPLOYEE_STATES = Set.of("ACTIVO","VACACIONES","SUSPENDIDO","CESADO");
    private static final Set<String> EQUIPMENT_STATES = Set.of("OPERATIVO","EN MANTENIMIENTO","FUERA DE SERVICIO","DADO DE BAJA");

    private final FollowUpRepository followUps;
    private final ComplaintRepository complaints;
    private final EmployeeRepository employees;
    private final EquipmentRepository equipment;

    @GetMapping("/follow-ups")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public List<FollowUp> followUps() { return followUps.findAllByOrderByFollowUpDateAsc(); }

    @PostMapping("/follow-ups")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public FollowUp createFollowUp(@RequestBody FollowUp item) {
        item.setId(null); item.setFollowUpStatus("PENDIENTE"); return followUps.save(item);
    }

    @PutMapping("/follow-ups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public FollowUp updateFollowUp(@PathVariable Long id, @RequestBody FollowUp incoming) {
        FollowUp current = followUps.findById(id).orElseThrow();
        current.setPatientName(incoming.getPatientName()); current.setTreatment(incoming.getTreatment());
        current.setDoctorName(incoming.getDoctorName()); current.setTreatmentDate(incoming.getTreatmentDate());
        current.setFollowUpDate(incoming.getFollowUpDate()); current.setSymptoms(incoming.getSymptoms());
        current.setRecommendations(incoming.getRecommendations());
        if (incoming.getFollowUpStatus() != null) current.setFollowUpStatus(valid(incoming.getFollowUpStatus(), FOLLOW_UP_STATES, "seguimiento"));
        return followUps.save(current);
    }

    @PatchMapping("/follow-ups/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public FollowUp followUpStatus(@PathVariable Long id, @RequestParam String value) {
        FollowUp current = followUps.findById(id).orElseThrow();
        current.setFollowUpStatus(valid(value, FOLLOW_UP_STATES, "seguimiento")); return followUps.save(current);
    }

    @GetMapping("/complaints")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public List<Complaint> complaints() { return complaints.findAllByOrderByCreatedAtDesc(); }

    @PostMapping("/complaints")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Complaint createComplaint(@RequestBody Complaint item) {
        item.setId(null); item.setComplaintStatus("REGISTRADO"); return complaints.save(item);
    }

    @PutMapping("/complaints/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Complaint updateComplaint(@PathVariable Long id, @RequestBody Complaint incoming) {
        Complaint current = complaints.findById(id).orElseThrow();
        current.setType(incoming.getType()); current.setPatientName(incoming.getPatientName());
        current.setArea(incoming.getArea()); current.setDescription(incoming.getDescription()); current.setCreatedAt(incoming.getCreatedAt());
        current.setDueDate(incoming.getDueDate()); current.setResponsible(incoming.getResponsible()); current.setResponse(incoming.getResponse());
        if (incoming.getComplaintStatus() != null) current.setComplaintStatus(valid(incoming.getComplaintStatus(), COMPLAINT_STATES, "reclamo"));
        return complaints.save(current);
    }

    @PatchMapping("/complaints/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Complaint complaintStatus(@PathVariable Long id, @RequestParam String value) {
        Complaint current = complaints.findById(id).orElseThrow();
        current.setComplaintStatus(valid(value, COMPLAINT_STATES, "reclamo")); return complaints.save(current);
    }

    @GetMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Employee> employees() { return employees.findAllByOrderByFullNameAsc(); }

    @PostMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    public Employee createEmployee(@RequestBody Employee item) {
        item.setId(null); item.setEmployeeStatus("ACTIVO"); return employees.save(item);
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Employee updateEmployee(@PathVariable Long id, @RequestBody Employee incoming) {
        Employee current = employees.findById(id).orElseThrow();
        current.setFullName(incoming.getFullName()); current.setPosition(incoming.getPosition()); current.setArea(incoming.getArea());
        current.setBranch(incoming.getBranch()); current.setShift(incoming.getShift()); current.setContractEnd(incoming.getContractEnd());
        if (incoming.getEmployeeStatus() != null) current.setEmployeeStatus(valid(incoming.getEmployeeStatus(), EMPLOYEE_STATES, "empleado"));
        return employees.save(current);
    }

    @PatchMapping("/employees/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Employee employeeStatus(@PathVariable Long id, @RequestParam String value) {
        Employee current = employees.findById(id).orElseThrow();
        current.setEmployeeStatus(valid(value, EMPLOYEE_STATES, "empleado")); return employees.save(current);
    }

    @GetMapping("/equipment")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public List<Equipment> equipment() { return equipment.findAllByOrderByNextMaintenanceAsc(); }

    @PostMapping("/equipment")
    @PreAuthorize("hasRole('ADMIN')")
    public Equipment createEquipment(@RequestBody Equipment item) {
        item.setId(null); item.setEquipmentStatus("OPERATIVO"); return equipment.save(item);
    }

    @PutMapping("/equipment/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Equipment updateEquipment(@PathVariable Long id, @RequestBody Equipment incoming) {
        Equipment current = equipment.findById(id).orElseThrow();
        current.setName(incoming.getName()); current.setBrand(incoming.getBrand()); current.setModel(incoming.getModel());
        current.setSerialNumber(incoming.getSerialNumber()); current.setLocation(incoming.getLocation());
        current.setLastMaintenance(incoming.getLastMaintenance()); current.setNextMaintenance(incoming.getNextMaintenance());
        if (incoming.getEquipmentStatus() != null) current.setEquipmentStatus(valid(incoming.getEquipmentStatus(), EQUIPMENT_STATES, "equipo"));
        return equipment.save(current);
    }

    @PatchMapping("/equipment/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Equipment equipmentStatus(@PathVariable Long id, @RequestParam String value) {
        Equipment current = equipment.findById(id).orElseThrow();
        current.setEquipmentStatus(valid(value, EQUIPMENT_STATES, "equipo")); return equipment.save(current);
    }

    private String valid(String value, Set<String> allowed, String resource) {
        String requested = value == null ? "" : value.trim();
        return allowed.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de " + resource + " no permitido"));
    }
}
