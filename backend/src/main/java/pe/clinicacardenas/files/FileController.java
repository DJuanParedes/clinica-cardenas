package pe.clinicacardenas.files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Archivos clínicos del paciente para el personal (web y app del odontólogo). */
@RestController
@RequestMapping("/api")
public class FileController {

    private static final String UPLOAD = "hasAnyAuthority('HISTORIA_CLINICA','PACIENTES_EDITAR')";
    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @GetMapping("/patients/{id}/files")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public List<PatientFile> list(@PathVariable Long id) {
        return service.list(id);
    }

    @PostMapping(value = "/patients/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(UPLOAD)
    public PatientFile upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String kind, @RequestParam(required = false) String title,
                              @RequestParam(required = false) String note, @RequestParam(required = false) Integer tooth,
                              @RequestParam(defaultValue = "false") boolean share) {
        return service.upload(id, file, kind, title, note, tooth, share);
    }

    @PostMapping("/patients/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(UPLOAD)
    public PatientFile document(@PathVariable Long id, @RequestBody FileService.DocumentRequest req) {
        return service.document(id, req);
    }

    @GetMapping("/files/{id}/content")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        return serve(service, service.get(id));
    }

    @PatchMapping("/files/{id}")
    @PreAuthorize(UPLOAD)
    public PatientFile share(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return service.share(id, Boolean.TRUE.equals(body.get("sharedWithPatient")));
    }

    @DeleteMapping("/files/{id}")
    @PreAuthorize("hasAuthority('HISTORIA_CLINICA')")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Archivo eliminado.");
    }

    static ResponseEntity<byte[]> serve(FileService service, PatientFile f) {
        String ext = f.contentType.endsWith("pdf") ? ".pdf" : f.contentType.endsWith("png") ? ".png" : ".jpg";
        String name = f.title.replaceAll("[^\\p{L}\\p{N} _-]", "").trim().replace(' ', '_') + ext;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(service.content(f));
    }
}
