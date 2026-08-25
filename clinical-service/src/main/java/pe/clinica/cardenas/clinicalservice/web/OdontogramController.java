package pe.clinica.cardenas.clinicalservice.web;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.clinicalservice.domain.OdontogramRecord;
import pe.clinica.cardenas.clinicalservice.repository.OdontogramRecordRepository;
import pe.clinica.cardenas.clinicalservice.service.FdiOdontogramStandard;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/odontograms")
@RequiredArgsConstructor
public class OdontogramController {
    private final OdontogramRecordRepository repository;
    private final FdiOdontogramStandard fdiStandard;

    /** Catálogo consumible por la interfaz y por integraciones clínicas. */
    @GetMapping("/standard")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Map<String, Object> standard() {
        return fdiStandard.metadata();
    }

    /**
     * Endpoint principal. El nombre viaja como query parameter para evitar problemas
     * de codificación de espacios, tildes o caracteres especiales en el API Gateway.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    public OdontogramResponse getByQuery(@RequestParam(required = false) @Positive Long patientId,
                                          @RequestParam(required = false) String patientName) {
        return find(patientId, patientName);
    }

    /** Ruta conservada para compatibilidad con clientes anteriores. */
    @GetMapping("/{patientName}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    public OdontogramResponse getLegacy(@PathVariable String patientName) {
        return find(null, patientName);
    }

    /**
     * Endpoint principal de guardado. Todo el registro viaja en JSON y no en la URL.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    public OdontogramResponse save(@Valid @RequestBody SaveOdontogramRequest incoming,
                                    Authentication authentication) {
        return persist(incoming.patientId(), incoming.patientName(), incoming, authentication);
    }

    /** Ruta conservada para compatibilidad con clientes anteriores. */
    @PutMapping("/{patientName}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    public OdontogramResponse saveLegacy(@PathVariable String patientName,
                                          @RequestBody SaveOdontogramRequest incoming,
                                          Authentication authentication) {
        return persist(incoming.patientId(), patientName, incoming, authentication);
    }

    private OdontogramResponse find(Long patientId, String patientName) {
        if (patientId != null) {
            var byId = repository.findByPatientId(patientId);
            if (byId.isPresent()) return OdontogramResponse.from(byId.get(), fdiStandard);
        }
        String normalizedName = normalizePatientName(patientName);
        return (patientId == null ? repository.findFirstByPatientNameIgnoreCaseOrderByUpdatedAtDesc(normalizedName)
                : java.util.Optional.<OdontogramRecord>empty())
                .map(value -> OdontogramResponse.from(value, fdiStandard))
                .orElseGet(() -> new OdontogramResponse(null, patientId, normalizedName, fdiStandard.emptyStateJson(),
                        "", "", null, null, FdiOdontogramStandard.STANDARD_ID));
    }

    private OdontogramResponse persist(Long patientId, String patientName, SaveOdontogramRequest incoming,
                                        Authentication authentication) {
        String normalizedName = normalizePatientName(patientName);
        OdontogramRecord current;
        if (patientId == null) {
            current = repository.findFirstByPatientNameIgnoreCaseOrderByUpdatedAtDesc(normalizedName)
                    .orElseGet(OdontogramRecord::new);
        } else {
            current = repository.findByPatientId(patientId)
                    .orElseGet(() -> repository.findFirstByPatientNameIgnoreCaseOrderByUpdatedAtDesc(normalizedName)
                            .filter(value -> value.getPatientId() == null)
                            .orElseGet(OdontogramRecord::new));
            current.setPatientId(patientId);
        }

        current.setPatientName(normalizedName);
        current.setStateJson(fdiStandard.normalizeAndValidate(incoming.stateJson()));
        current.setDiagnosis(normalizeText(incoming.diagnosis(), ""));
        current.setTreatment(normalizeText(incoming.treatment(), ""));
        current.setUpdatedBy(authentication == null ? "sistema" : authentication.getName());
        current.setUpdatedAt(LocalDateTime.now());

        OdontogramRecord saved = repository.saveAndFlush(current);
        return OdontogramResponse.from(saved, fdiStandard);
    }

    private String normalizePatientName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El nombre del paciente es obligatorio");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("El nombre del paciente supera los 120 caracteres");
        }
        return normalized;
    }

    private String normalizeText(String value, String defaultValue) {
        return value == null ? defaultValue : value.trim();
    }

    public record SaveOdontogramRequest(
            @Positive(message = "debe ser positivo") Long patientId,
            @NotBlank(message = "es obligatorio") String patientName,
            String stateJson,
            String diagnosis,
            String treatment
    ) {}

    public record OdontogramResponse(
            Long id,
            Long patientId,
            String patientName,
            String stateJson,
            String diagnosis,
            String treatment,
            String updatedBy,
            LocalDateTime updatedAt,
            String standard
    ) {
        static OdontogramResponse from(OdontogramRecord value, FdiOdontogramStandard fdiStandard) {
            return new OdontogramResponse(
                    value.getId(), value.getPatientId(), value.getPatientName(), fdiStandard.normalizeAndValidate(value.getStateJson()),
                    value.getDiagnosis(), value.getTreatment(), value.getUpdatedBy(), value.getUpdatedAt(),
                    FdiOdontogramStandard.STANDARD_ID
            );
        }
    }
}
