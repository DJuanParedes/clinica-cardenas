package pe.clinicacardenas.patient;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Política de privacidad y términos vigentes (públicos) y consentimientos de cada paciente. */
@RestController
public class LegalController {

    private final ConsentService consents;
    private final DniLookupService dni;

    public LegalController(ConsentService consents, DniLookupService dni) {
        this.consents = consents;
        this.dni = dni;
    }

    @GetMapping("/api/public/legal/{code}")
    public Map<String, Object> document(@PathVariable String code) {
        return consents.document(code);
    }

    @GetMapping("/api/patients/{id}/consents")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public Map<String, Object> consents(@PathVariable Long id) {
        return consents.history(id);
    }

    @PostMapping(value = "/api/patients/{id}/consents/signed", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> signed(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return consents.attachSigned(id, file);
    }

    /** Autocompletar nombres con el DNI (RENIEC, mediante un proveedor de datos abiertos). */
    @GetMapping("/api/lookup/dni/{number}")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Map<String, Object> lookupDni(@PathVariable String number) {
        return dni.lookup(number);
    }

    @GetMapping("/api/lookup/dni-config")
    public Map<String, Object> lookupConfig() {
        return Map.of("enabled", dni.enabled());
    }
}
