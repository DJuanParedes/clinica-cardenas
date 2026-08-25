package pe.clinica.cardenas.authservice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.authservice.domain.Clinic;
import pe.clinica.cardenas.authservice.domain.UserAccount;
import pe.clinica.cardenas.authservice.repository.ClinicRepository;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/clinics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClinicAdminController {
    private final ClinicRepository clinics;
    private final UserAccountRepository users;

    @GetMapping
    public List<ClinicBrand> list(Authentication authentication) {
        UserAccount account = current(authentication);
        if (account.isPlatformAdmin()) {
            return clinics.findAll().stream().sorted(Comparator.comparing(Clinic::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(ClinicBrand::from).toList();
        }
        return clinics.findByCodeIgnoreCase(account.getClinicId()).stream().map(ClinicBrand::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicBrand create(Authentication authentication, @Valid @RequestBody ClinicRequest request) {
        requirePlatform(authentication);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        if (clinics.existsByCodeIgnoreCase(code)) throw conflict("El código de clínica ya existe");
        if (clinics.existsBySlugIgnoreCase(slug)) throw conflict("La dirección de la marca ya existe");
        LocalDateTime now = LocalDateTime.now();
        Clinic clinic = Clinic.builder().code(code).slug(slug).createdAt(now).updatedAt(now).active(true).build();
        apply(clinic, request);
        return ClinicBrand.from(clinics.save(clinic));
    }

    @PutMapping("/{id}")
    public ClinicBrand update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody ClinicRequest request) {
        requirePlatform(authentication);
        Clinic clinic = find(id);
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        clinics.findBySlugIgnoreCase(slug).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw conflict("La dirección de la marca ya existe"); });
        clinic.setSlug(slug);
        apply(clinic, request);
        clinic.setUpdatedAt(LocalDateTime.now());
        return ClinicBrand.from(clinics.save(clinic));
    }

    @PatchMapping("/{id}/active")
    public ClinicBrand active(Authentication authentication, @PathVariable Long id, @RequestParam boolean value) {
        requirePlatform(authentication);
        Clinic clinic = find(id);
        if (clinic.getCode().equalsIgnoreCase("CARDENAS") && !value) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La clínica principal no puede desactivarse");
        }
        clinic.setActive(value); clinic.setUpdatedAt(LocalDateTime.now());
        return ClinicBrand.from(clinics.save(clinic));
    }

    private void apply(Clinic clinic, ClinicRequest request) {
        clinic.setName(request.name().trim());
        clinic.setShortName(request.shortName().trim());
        clinic.setTagline(clean(request.tagline())); clinic.setDescription(clean(request.description()));
        clinic.setLogoUrl(clean(request.logoUrl())); clinic.setHeroImageUrl(clean(request.heroImageUrl()));
        clinic.setPrimaryColor(request.primaryColor()); clinic.setPrimaryDarkColor(request.primaryDarkColor());
        clinic.setAccentColor(request.accentColor()); clinic.setAccentDarkColor(request.accentDarkColor());
        clinic.setSidebarDarkColor(request.sidebarDarkColor()); clinic.setPhone(clean(request.phone()));
        clinic.setEmail(clean(request.email())); clinic.setAddress(clean(request.address()));
        clinic.setWorkingHours(clean(request.workingHours()));
        clinic.setCustomDomain(request.customDomain() == null || request.customDomain().isBlank() ? null : request.customDomain().trim().toLowerCase());
    }

    private UserAccount current(Authentication authentication) {
        return users.findByEmail(authentication.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }
    private void requirePlatform(Authentication authentication) {
        if (!current(authentication).isPlatformAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el superadministrador administra marcas");
    }
    private Clinic find(Long id) { return clinics.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clínica no encontrada")); }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record ClinicRequest(
            @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{1,39}") String code,
            @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{1,59}") String slug,
            @NotBlank @Size(max=120) String name, @NotBlank @Size(max=80) String shortName,
            @Size(max=160) String tagline, @Size(max=600) String description,
            @Size(max=300) String logoUrl, @Size(max=300) String heroImageUrl,
            @NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String primaryColor,
            @NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String primaryDarkColor,
            @NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String accentColor,
            @NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String accentDarkColor,
            @NotBlank @Pattern(regexp="#[0-9A-Fa-f]{6}") String sidebarDarkColor,
            @Size(max=30) String phone, @Email @Size(max=160) String email,
            @Size(max=220) String address, @Size(max=160) String workingHours,
            @Size(max=160) String customDomain) {}
}
