package pe.clinicacardenas.ortho;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
public class OrthoController {

    private final OrthoService service;

    public OrthoController(OrthoService service) {
        this.service = service;
    }

    @GetMapping("/ortho/options")
    public Map<String, Object> options() { return service.options(); }

    @GetMapping("/ortho/cases")
    public List<Map<String, Object>> active(@RequestParam(required = false) Long dentistId) { return service.active(dentistId); }

    @GetMapping("/patients/{patientId}/ortho")
    public List<Map<String, Object>> forPatient(@PathVariable Long patientId) { return service.forPatient(patientId); }

    @PostMapping("/patients/{patientId}/ortho")
    public Map<String, Object> create(@PathVariable Long patientId, @RequestBody OrthoService.CaseRequest r) { return service.create(patientId, r); }

    @PutMapping("/ortho/cases/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody OrthoService.CaseRequest r) { return service.update(id, r); }

    @PostMapping("/ortho/cases/{id}/controls")
    public Map<String, Object> control(@PathVariable Long id, @RequestBody OrthoService.ControlRequest r) { return service.addControl(id, r); }

    @PostMapping(value = "/ortho/cases/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file, @RequestParam String stage,
                                      @RequestParam String view, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn) {
        return service.upload(id, file, stage, view, takenOn);
    }

    @PostMapping("/ortho/cases/{id}/photos/link")
    public Map<String, Object> link(@PathVariable Long id, @RequestBody OrthoService.LinkRequest r) { return service.linkExisting(id, r); }

    @PutMapping("/ortho/cases/{id}/photos/{photoId}")
    public Map<String, Object> move(@PathVariable Long id, @PathVariable Long photoId, @RequestBody OrthoService.LinkRequest r) { return service.movePhoto(id, photoId, r); }

    @DeleteMapping("/ortho/cases/{id}/photos/{photoId}")
    public Map<String, Object> unlink(@PathVariable Long id, @PathVariable Long photoId) { return service.unlink(id, photoId); }
}
