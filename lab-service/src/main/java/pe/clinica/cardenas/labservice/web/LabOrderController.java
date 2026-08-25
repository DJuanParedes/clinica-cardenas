package pe.clinica.cardenas.labservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.labservice.domain.LabOrder;
import pe.clinica.cardenas.labservice.repository.LabOrderRepository;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/lab-orders")
@RequiredArgsConstructor
public class LabOrderController {
    private static final Set<String> STATUSES = Set.of("SOLICITUD CREADA","ENVIADO","EN FABRICACIÓN","LISTO PARA ENTREGA","RECIBIDO","REQUIERE CORRECCIÓN","INSTALADO","FINALIZADO");
    private final LabOrderRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<LabOrder> list() { return repository.findAllByOrderByExpectedDateAsc(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public LabOrder create(@RequestBody LabOrder order) {
        order.setId(null);
        order.setLabStatus("SOLICITUD CREADA");
        return repository.save(order);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public LabOrder update(@PathVariable Long id, @RequestBody LabOrder incoming) {
        LabOrder current = repository.findById(id).orElseThrow();
        current.setPatientName(incoming.getPatientName());
        current.setDoctorName(incoming.getDoctorName());
        current.setLaboratory(incoming.getLaboratory());
        current.setWorkType(incoming.getWorkType());
        current.setToothPieces(incoming.getToothPieces());
        current.setMaterial(incoming.getMaterial());
        current.setShade(incoming.getShade());
        current.setSentDate(incoming.getSentDate());
        current.setExpectedDate(incoming.getExpectedDate());
        current.setReceivedDate(incoming.getReceivedDate());
        current.setCost(incoming.getCost());
        current.setNotes(incoming.getNotes());
        if (incoming.getLabStatus() != null) current.setLabStatus(validStatus(incoming.getLabStatus()));
        return repository.save(current);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public LabOrder status(@PathVariable Long id, @RequestParam String value) {
        LabOrder order = repository.findById(id).orElseThrow();
        order.setLabStatus(validStatus(value));
        return repository.save(order);
    }
    private String validStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de laboratorio no permitido"));
    }

}
