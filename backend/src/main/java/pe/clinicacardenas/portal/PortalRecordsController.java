package pe.clinicacardenas.portal;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Ficha del paciente vista por él mismo, consentimientos y archivos solicitados (paciente y personal). */
@RestController
public class PortalRecordsController {

    private final PortalRecordsService service;

    public PortalRecordsController(PortalRecordsService service) {
        this.service = service;
    }

    @PutMapping("/api/portal/me/contact")
    public Map<String, Object> contact(Authentication a, @RequestBody PortalRecordsService.ContactRequest r) { return service.updateContact(a.getName(), r); }

    @GetMapping("/api/portal/treatments")
    public Map<String, Object> treatments(Authentication a) { return service.treatments(a.getName()); }

    @GetMapping("/api/portal/consents")
    public Map<String, Object> consents(Authentication a) { return service.consents(a.getName()); }

    @GetMapping("/api/portal/consents/{id}/pdf")
    public ResponseEntity<byte[]> consentPdf(Authentication a, @PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"consentimiento-" + id + ".pdf\"").body(service.consentPdf(a.getName(), id));
    }

    @GetMapping("/api/portal/files/{id}/link")
    public Map<String, String> fileLink(Authentication a, @PathVariable Long id) { return service.fileLink(a.getName(), id); }

    @GetMapping("/api/portal/file-requests")
    public List<Map<String, Object>> myRequests(Authentication a) { return service.myFileRequests(a.getName()); }

    @PostMapping(value = "/api/portal/file-requests/{id}/upload", consumes = "multipart/form-data")
    public List<Map<String, Object>> patientUpload(Authentication a, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.patientUpload(a.getName(), id, file);
    }

    // ------------------------------------------------------------------ personal

    @GetMapping("/api/patients/{patientId}/file-requests")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public List<Map<String, Object>> list(@PathVariable Long patientId) { return service.staffFileRequests(patientId); }

    @PostMapping("/api/patients/{patientId}/file-requests")
    @PreAuthorize("hasAnyAuthority('HISTORIA_CLINICA','PACIENTES_EDITAR')")
    public List<Map<String, Object>> create(@PathVariable Long patientId, @RequestBody PortalRecordsService.NewFileRequest r) {
        return service.createRequest(patientId, r);
    }

    @PostMapping("/api/patients/{patientId}/file-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('HISTORIA_CLINICA','PACIENTES_EDITAR')")
    public List<Map<String, Object>> cancel(@PathVariable Long patientId, @PathVariable Long id) { return service.cancelRequest(patientId, id); }

    @PostMapping(value = "/api/patients/{patientId}/file-requests/{id}/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('PACIENTES_EDITAR','HISTORIA_CLINICA')")
    public List<Map<String, Object>> staffUpload(@PathVariable Long patientId, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.staffUpload(patientId, id, file);
    }
}
