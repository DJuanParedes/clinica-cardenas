package pe.clinica.cardenas.clinicalservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.clinicalservice.domain.ClinicalDocument;
import pe.clinica.cardenas.clinicalservice.repository.ClinicalDocumentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/operations/documents")
@RequiredArgsConstructor
public class ClinicalDocumentController {
    private static final Set<String> STATUSES = Set.of("VIGENTE","FIRMADO","REEMPLAZADO","ANULADO");
    private final ClinicalDocumentRepository documents;

    @Value("${clinical.storage.path:./storage}")
    private String storagePath;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public List<ClinicalDocument> list() { return documents.findAllByOrderByUploadedAtDesc(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ClinicalDocument create(@RequestBody ClinicalDocument item, Authentication authentication) {
        item.setId(null);
        if (item.getUploadedAt() == null) item.setUploadedAt(LocalDateTime.now());
        if (item.getUploadedBy() == null || item.getUploadedBy().isBlank()) item.setUploadedBy(authentication.getName());
        item.setDocumentStatus("VIGENTE");
        return documents.save(item);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ClinicalDocument upload(@RequestParam String patientName,
                                   @RequestParam String category,
                                   @RequestParam(defaultValue = "1.0") String version,
                                   @RequestParam(required = false) String notes,
                                   @RequestPart("file") MultipartFile file,
                                   Authentication authentication) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo");
        if (file.getSize() > 25L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera 25 MB");
        Path root = Path.of(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String original = file.getOriginalFilename() == null ? "documento" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String storageKey = UUID.randomUUID() + extension.toLowerCase();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        ClinicalDocument document = ClinicalDocument.builder()
                .patientName(patientName.trim()).category(category.trim()).fileName(original).version(version)
                .uploadedBy(authentication.getName()).uploadedAt(LocalDateTime.now()).documentStatus("VIGENTE")
                .storageKey(storageKey).contentType(file.getContentType()).fileSize(file.getSize()).notes(notes)
                .build();
        return documents.save(document);
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ClinicalDocument document = find(id);
        if (document.getStorageKey() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Este registro no tiene un archivo asociado");
        Path path = Path.of(storagePath).toAbsolutePath().normalize().resolve(document.getStorageKey()).normalize();
        if (!Files.exists(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado en almacenamiento");
        MediaType type;
        try { type = document.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(document.getContentType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.getFileName()).build().toString())
                .body(new FileSystemResource(path));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ClinicalDocument update(@PathVariable Long id, @RequestBody ClinicalDocument incoming) {
        ClinicalDocument current = find(id);
        current.setPatientName(incoming.getPatientName());
        current.setCategory(incoming.getCategory());
        current.setFileName(incoming.getFileName());
        current.setVersion(incoming.getVersion());
        current.setNotes(incoming.getNotes());
        if (incoming.getDocumentStatus() != null) current.setDocumentStatus(validStatus(incoming.getDocumentStatus()));
        return documents.save(current);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ClinicalDocument status(@PathVariable Long id, @RequestParam String value) {
        ClinicalDocument current = find(id);
        current.setDocumentStatus(validStatus(value));
        return documents.save(current);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) throws IOException {
        ClinicalDocument current = find(id);
        if (current.getStorageKey() != null) {
            Files.deleteIfExists(Path.of(storagePath).toAbsolutePath().normalize().resolve(current.getStorageKey()).normalize());
        }
        documents.delete(current);
    }

    private ClinicalDocument find(Long id) {
        return documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado"));
    }
    private String validStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de documento no permitido"));
    }

}
