package pe.clinica.cardenas.contentservice.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.contentservice.domain.PublicInquiry;
import pe.clinica.cardenas.contentservice.repository.PublicInquiryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class InquiryController {
    private static final Set<String> STATUSES = Set.of("NUEVA","CONTACTADA","CITA PROGRAMADA","ATENDIDA","DESCARTADA");
    private final PublicInquiryRepository repository;

    @PostMapping("/api/public/inquiries")
    public PublicInquiry publicCreate(@Valid @RequestBody PublicInquiry incoming) {
        incoming.setId(null); incoming.setCreatedAt(LocalDateTime.now()); incoming.setStatus("NUEVA"); incoming.setAssignedTo("Recepción");
        return repository.save(incoming);
    }

    @GetMapping("/api/inquiries")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public List<PublicInquiry> list() { return repository.findAllByOrderByCreatedAtDesc(); }

    @PostMapping("/api/inquiries")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public PublicInquiry create(@Valid @RequestBody PublicInquiry incoming) {
        incoming.setId(null); incoming.setCreatedAt(LocalDateTime.now()); incoming.setStatus("NUEVA");
        return repository.save(incoming);
    }

    @PutMapping("/api/inquiries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public PublicInquiry update(@PathVariable Long id, @Valid @RequestBody PublicInquiry incoming) {
        PublicInquiry current = repository.findById(id).orElseThrow();
        current.setFullName(incoming.getFullName()); current.setPhone(incoming.getPhone()); current.setSpecialty(incoming.getSpecialty());
        current.setMessage(incoming.getMessage()); current.setAssignedTo(incoming.getAssignedTo());
        if (incoming.getStatus() != null) current.setStatus(validStatus(incoming.getStatus()));
        return repository.save(current);
    }

    @PatchMapping("/api/inquiries/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public PublicInquiry status(@PathVariable Long id, @RequestParam String value) {
        PublicInquiry current = repository.findById(id).orElseThrow(); current.setStatus(validStatus(value)); return repository.save(current);
    }

    private String validStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de solicitud no permitido"));
    }
}
