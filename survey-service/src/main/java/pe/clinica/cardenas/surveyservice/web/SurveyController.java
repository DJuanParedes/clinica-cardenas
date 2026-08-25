package pe.clinica.cardenas.surveyservice.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import pe.clinica.cardenas.surveyservice.domain.SurveyResponse;
import pe.clinica.cardenas.surveyservice.domain.SurveyToken;
import pe.clinica.cardenas.surveyservice.repository.SurveyResponseRepository;
import pe.clinica.cardenas.surveyservice.repository.SurveyTokenRepository;
import pe.clinica.cardenas.surveyservice.web.dto.SurveySubmissionRequest;
import pe.clinica.cardenas.surveyservice.web.dto.SurveyTokenRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SurveyController {
    private static final Set<String> FOLLOW_UP_STATUSES = Set.of("PENDIENTE","CONTACTADO","RESUELTO","NO_REQUERIDO");
    private final SurveyTokenRepository tokens;
    private final SurveyResponseRepository responses;

    @PostMapping("/api/surveys/tokens")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    public SurveyToken token(@RequestBody SurveyTokenRequest request) {
        SurveyToken token = new SurveyToken();
        token.setCode("ENC-" + shortCode() + "-" + shortCode());
        token.setEncounterId(request.encounterId());
        token.setPatientId(request.patientId());
        token.setDoctorName(request.doctorName());
        token.setSpecialty(request.specialty());
        token.setBranch(request.branch());
        token.setChairName(request.chairName());
        token.setExpiresAt(LocalDateTime.now().plusDays(30));
        token.setUsed(false);
        return tokens.save(token);
    }

    @GetMapping("/api/public/surveys/token/{code}")
    public Map<String, Object> validate(@PathVariable String code) {
        SurveyToken token = findUsableToken(code, HttpStatus.NOT_FOUND);
        return Map.of(
                "valid", true,
                "specialty", text(token.getSpecialty()),
                "branch", text(token.getBranch())
        );
    }

    @PostMapping("/api/public/surveys")
    @Transactional
    public Map<String, Object> submit(@Valid @RequestBody SurveySubmissionRequest request) {
        boolean anonymous = Boolean.TRUE.equals(request.anonymous());
        boolean contactRequested = Boolean.TRUE.equals(request.contactRequested());

        if (contactRequested && text(request.contactPhone()).isBlank() && text(request.contactEmail()).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica un teléfono o correo para poder contactarte"
            );
        }

        SurveyResponse response = new SurveyResponse();
        response.setSurveyCode(normalizeCode(request.surveyCode()));
        response.setReceptionScore(request.receptionScore());
        response.setKindnessScore(request.kindnessScore());
        response.setWaitTimeScore(request.waitTimeScore());
        response.setExplanationScore(request.explanationScore());
        response.setDoctorTreatmentScore(request.doctorTreatmentScore());
        response.setCleanlinessScore(request.cleanlinessScore());
        response.setComfortScore(request.comfortScore());
        response.setPriceClarityScore(request.priceClarityScore());
        response.setOverallScore(request.overallScore());
        response.setRecommend(request.recommend());
        response.setLikedMost(request.likedMost());
        response.setImprovement(request.improvement());
        response.setAdditionalComment(request.additionalComment());
        response.setAnonymous(anonymous);

        if (anonymous) {
            response.setContactRequested(false);
            response.setContactName(null);
            response.setContactPhone(null);
            response.setContactEmail(null);
        } else {
            response.setContactRequested(contactRequested);
            response.setContactName(request.contactName());
            response.setContactPhone(request.contactPhone());
            response.setContactEmail(request.contactEmail());
        }

        SurveyToken linkedToken = null;
        if (response.getSurveyCode() != null) {
            linkedToken = findUsableToken(response.getSurveyCode(), HttpStatus.BAD_REQUEST);
            response.setEncounterId(linkedToken.getEncounterId());
            response.setDoctorName(linkedToken.getDoctorName());
            response.setSpecialty(linkedToken.getSpecialty());
            response.setBranch(linkedToken.getBranch());
            response.setChairName(linkedToken.getChairName());
        }

        response.setSubmittedAt(LocalDateTime.now());
        response.setRequiresAttention(average(response) < 3 || Boolean.TRUE.equals(response.getContactRequested()));
        response.setFollowUpStatus(response.isRequiresAttention() ? "PENDIENTE" : "NO_REQUERIDO");

        SurveyResponse saved = responses.save(response);
        if (linkedToken != null) {
            linkedToken.setUsed(true);
            tokens.save(linkedToken);
        }

        return Map.of(
                "id", saved.getId(),
                "status", "REGISTRADA",
                "message", "Encuesta registrada correctamente"
        );
    }

    @GetMapping("/api/surveys/responses")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public List<SurveyResponse> responses() {
        return responses.findTop500ByOrderBySubmittedAtDesc();
    }

    @GetMapping("/api/surveys/summary")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Map<String, Object> summary() {
        List<SurveyResponse> all = responses.findAll();
        double average = all.stream().mapToDouble(this::average).average().orElse(0);
        return Map.of(
                "responses", all.size(),
                "average", Math.round(average * 100.0) / 100.0,
                "alerts", all.stream().filter(SurveyResponse::isRequiresAttention).count(),
                "recommendRate", all.isEmpty()
                        ? 0
                        : Math.round(all.stream().filter(item -> Boolean.TRUE.equals(item.getRecommend())).count() * 1000.0 / all.size()) / 10.0,
                "receptionAverage", averageField(all, "reception"),
                "waitAverage", averageField(all, "wait"),
                "priceClarityAverage", averageField(all, "price")
        );
    }

    @PatchMapping("/api/surveys/responses/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public SurveyResponse status(@PathVariable Long id, @RequestParam String value) {
        SurveyResponse response = responses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
        response.setFollowUpStatus(validFollowUpStatus(value));
        return responses.save(response);
    }


    private String validFollowUpStatus(String value) {
        String requested = value == null ? "" : value.trim();
        return FOLLOW_UP_STATUSES.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de seguimiento de encuesta no permitido"));
    }

    private SurveyToken findUsableToken(String code, HttpStatus invalidCodeStatus) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            throw new ResponseStatusException(invalidCodeStatus, "Código no válido");
        }
        SurveyToken token = tokens.findByCode(normalized)
                .orElseThrow(() -> new ResponseStatusException(invalidCodeStatus, "Código no válido"));
        if (token.isUsed() || token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "El código ya fue utilizado o venció");
        }
        return token;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String shortCode() {
        return UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private double averageField(List<SurveyResponse> all, String field) {
        return all.stream()
                .mapToInt(response -> switch (field) {
                    case "reception" -> number(response.getReceptionScore());
                    case "wait" -> number(response.getWaitTimeScore());
                    default -> number(response.getPriceClarityScore());
                })
                .filter(value -> value > 0)
                .average()
                .orElse(0);
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private double average(SurveyResponse response) {
        return java.util.stream.Stream.of(
                        response.getReceptionScore(),
                        response.getKindnessScore(),
                        response.getWaitTimeScore(),
                        response.getExplanationScore(),
                        response.getDoctorTreatmentScore(),
                        response.getCleanlinessScore(),
                        response.getComfortScore(),
                        response.getPriceClarityScore(),
                        response.getOverallScore()
                )
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
