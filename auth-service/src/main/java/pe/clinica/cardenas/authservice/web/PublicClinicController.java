package pe.clinica.cardenas.authservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import pe.clinica.cardenas.authservice.domain.Clinic;
import pe.clinica.cardenas.authservice.repository.ClinicRepository;

import java.util.List;

@RestController
@RequestMapping("/api/public/clinics")
@RequiredArgsConstructor
public class PublicClinicController {
    private final ClinicRepository clinics;

    @GetMapping
    public List<ClinicBrand> list() {
        return clinics.findAllByActiveTrueOrderByNameAsc().stream().map(ClinicBrand::from).toList();
    }

    @GetMapping("/{slug}")
    public ClinicBrand bySlug(@PathVariable String slug) {
        Clinic clinic = clinics.findBySlugIgnoreCase(slug.trim())
                .filter(Clinic::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clínica no encontrada"));
        return ClinicBrand.from(clinic);
    }

    @GetMapping("/resolve-domain")
    public ClinicBrand byDomain(@RequestParam String domain) {
        String normalized = domain == null ? "" : domain.trim().toLowerCase();
        Clinic clinic = clinics.findByCustomDomainIgnoreCase(normalized)
                .filter(Clinic::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dominio no asociado"));
        return ClinicBrand.from(clinic);
    }
}
