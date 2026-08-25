package pe.clinica.cardenas.clinicalservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Normaliza y valida el estado del odontograma usando la notación dental
 * internacional de dos dígitos FDI/ISO 3950:2016.
 *
 * El lector conserva compatibilidad con el formato histórico del proyecto:
 * {"16":"CARIES"}. Al responder o guardar, ese contenido se transforma al
 * esquema estructurado vigente sin modificar los registros directamente en una
 * operación de solo lectura.
 */
@Service
public class FdiOdontogramStandard {
    public static final String STANDARD_ID = "FDI_ISO_3950_2016";

    private static final List<Integer> PERMANENT_TEETH = List.of(
            18, 17, 16, 15, 14, 13, 12, 11,
            21, 22, 23, 24, 25, 26, 27, 28,
            48, 47, 46, 45, 44, 43, 42, 41,
            31, 32, 33, 34, 35, 36, 37, 38
    );
    private static final List<Integer> PRIMARY_TEETH = List.of(
            55, 54, 53, 52, 51,
            61, 62, 63, 64, 65,
            85, 84, 83, 82, 81,
            71, 72, 73, 74, 75
    );
    private static final Set<Integer> VALID_TEETH;
    private static final Set<String> DENTITIONS = Set.of("PERMANENTE", "TEMPORAL", "MIXTA");
    private static final Set<String> CONDITIONS = Set.of(
            "SANO", "CARIES", "RESTAURACIÓN", "AUSENTE", "ENDODONCIA",
            "CORONA", "IMPLANTE", "SELLANTE", "FRACTURA"
    );
    private static final Set<String> SURFACES = Set.of("M", "D", "V", "L", "O", "I");
    private static final Set<String> CARIES_STAGES = Set.of("NO_EVALUADA", "INICIAL", "MODERADA", "SEVERA");
    private static final Set<String> CARIES_ACTIVITIES = Set.of("NO_EVALUADA", "ACTIVA", "INACTIVA");
    private static final Set<String> CARIES_RISKS = Set.of("NO_EVALUADO", "BAJO", "MODERADO", "ALTO");

    static {
        LinkedHashSet<Integer> teeth = new LinkedHashSet<>(PERMANENT_TEETH);
        teeth.addAll(PRIMARY_TEETH);
        VALID_TEETH = Collections.unmodifiableSet(teeth);
    }

    private final ObjectMapper mapper;

    public FdiOdontogramStandard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String emptyStateJson() {
        return write(canonicalState("PERMANENTE", "NO_EVALUADO", List.of(), List.of(), null, Map.of()));
    }

    public String normalizeAndValidate(String rawJson) {
        if (rawJson == null || rawJson.isBlank() || "{}".equals(rawJson.trim())) return emptyStateJson();
        if (rawJson.length() > 100_000) fail("El odontograma supera el tamaño permitido");

        Map<String, Object> raw;
        try {
            raw = mapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado del odontograma no contiene JSON válido");
        }

        boolean structured = raw.containsKey("teeth") || raw.containsKey("standard");
        Map<String, Object> teethSource = structured ? objectMap(raw.get("teeth"), "teeth") : raw;
        LinkedHashMap<String, Object> findings = normalizeTeeth(teethSource, structured);

        String dentition = structured ? canonicalToken(raw.get("dentition"), "PERMANENTE") : inferDentition(findings.keySet());
        if (!DENTITIONS.contains(dentition)) fail("Dentición no permitida; usa PERMANENTE, TEMPORAL o MIXTA");
        validateDentition(dentition, findings.keySet());

        String cariesRisk = structured ? canonicalToken(raw.get("cariesRisk"), "NO_EVALUADO") : "NO_EVALUADO";
        if (!CARIES_RISKS.contains(cariesRisk)) fail("Riesgo de caries no permitido");

        List<String> riskFactors = structured ? textList(raw.get("riskFactors"), "riskFactors") : List.of();
        List<String> protectiveFactors = structured ? textList(raw.get("protectiveFactors"), "protectiveFactors") : List.of();
        String assessedAt = structured ? optionalDate(raw.get("assessedAt")) : null;

        return write(canonicalState(dentition, cariesRisk, riskFactors, protectiveFactors, assessedAt, findings));
    }

    public Map<String, Object> metadata() {
        LinkedHashMap<String, String> surfaceLabels = new LinkedHashMap<>();
        surfaceLabels.put("M", "Mesial");
        surfaceLabels.put("D", "Distal");
        surfaceLabels.put("V", "Vestibular");
        surfaceLabels.put("L", "Lingual / palatina");
        surfaceLabels.put("O", "Oclusal (posteriores)");
        surfaceLabels.put("I", "Incisal (anteriores)");

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("standard", STANDARD_ID);
        result.put("displayName", "Notación FDI de dos dígitos / ISO 3950:2016");
        result.put("permanentTeeth", PERMANENT_TEETH);
        result.put("primaryTeeth", PRIMARY_TEETH);
        result.put("dentitions", DENTITIONS.stream().sorted().toList());
        result.put("conditions", CONDITIONS.stream().sorted().toList());
        result.put("surfaces", surfaceLabels);
        result.put("cariesStages", List.of("NO_EVALUADA", "INICIAL", "MODERADA", "SEVERA"));
        result.put("cariesActivities", List.of("NO_EVALUADA", "ACTIVA", "INACTIVA"));
        result.put("cariesRisks", List.of("NO_EVALUADO", "BAJO", "MODERADO", "ALTO"));
        return result;
    }

    private LinkedHashMap<String, Object> normalizeTeeth(Map<String, Object> source, boolean structured) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey().startsWith("_")) continue;
            int tooth;
            try {
                tooth = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ex) {
                fail("Código dental FDI inválido: " + entry.getKey());
                return result;
            }
            if (!VALID_TEETH.contains(tooth)) fail("La pieza " + tooth + " no pertenece a la notación FDI válida");
            result.put(String.valueOf(tooth), normalizeFinding(tooth, entry.getValue(), structured));
        }
        return result;
    }

    private Map<String, Object> normalizeFinding(int tooth, Object raw, boolean structured) {
        LinkedHashMap<String, Object> finding = new LinkedHashMap<>();
        if (raw instanceof String legacyCondition) {
            finding.put("condition", condition(legacyCondition));
            finding.put("surfaces", List.of());
        } else {
            Map<String, Object> source = objectMap(raw, "pieza " + tooth);
            String condition = condition(Objects.toString(source.get("condition"), "SANO"));
            finding.put("condition", condition);
            finding.put("surfaces", normalizeSurfaces(tooth, source.get("surfaces")));
            copyText(source, finding, "diagnosis", 2_000);
            copyText(source, finding, "treatment", 2_000);
            if ("CARIES".equals(condition)) {
                String stage = canonicalToken(source.get("cariesStage"), "NO_EVALUADA");
                String activity = canonicalToken(source.get("cariesActivity"), "NO_EVALUADA");
                if (!CARIES_STAGES.contains(stage)) fail("Severidad de caries no permitida en la pieza " + tooth);
                if (!CARIES_ACTIVITIES.contains(activity)) fail("Actividad de caries no permitida en la pieza " + tooth);
                finding.put("cariesStage", stage);
                finding.put("cariesActivity", activity);
            }
        }
        if ("CARIES".equals(finding.get("condition"))) {
            finding.putIfAbsent("cariesStage", "NO_EVALUADA");
            finding.putIfAbsent("cariesActivity", "NO_EVALUADA");
        }
        return finding;
    }

    private List<String> normalizeSurfaces(int tooth, Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof Collection<?>)) fail("Las superficies de la pieza " + tooth + " deben ser una lista");
        Collection<?> values = (Collection<?>) raw;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            String surface = canonicalToken(value, "");
            if (!SURFACES.contains(surface)) fail("Superficie dental no permitida en la pieza " + tooth + ": " + surface);
            int position = tooth % 10;
            boolean anterior = position <= 3;
            if ("O".equals(surface) && anterior) fail("La superficie OCLUSAL no corresponde a la pieza anterior " + tooth);
            if ("I".equals(surface) && !anterior) fail("La superficie INCISAL no corresponde a la pieza posterior " + tooth);
            result.add(surface);
        }
        return List.copyOf(result);
    }

    private String condition(String value) {
        String token = canonicalToken(value, "SANO");
        token = switch (token) {
            case "RESTAURACION" -> "RESTAURACIÓN";
            default -> token;
        };
        if (!CONDITIONS.contains(token)) fail("Condición dental no permitida: " + value);
        return token;
    }

    private void validateDentition(String dentition, Set<String> teeth) {
        for (String value : teeth) {
            int tooth = Integer.parseInt(value);
            boolean primary = tooth >= 50;
            if ("PERMANENTE".equals(dentition) && primary) fail("La dentición PERMANENTE no admite la pieza temporal " + tooth);
            if ("TEMPORAL".equals(dentition) && !primary) fail("La dentición TEMPORAL no admite la pieza permanente " + tooth);
        }
    }

    private String inferDentition(Set<String> teeth) {
        boolean permanent = false;
        boolean primary = false;
        for (String value : teeth) {
            if (Integer.parseInt(value) >= 50) primary = true; else permanent = true;
        }
        if (permanent && primary) return "MIXTA";
        if (primary) return "TEMPORAL";
        return "PERMANENTE";
    }

    private Map<String, Object> canonicalState(String dentition, String cariesRisk, List<String> riskFactors,
                                                List<String> protectiveFactors, String assessedAt,
                                                Map<String, Object> findings) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("standard", STANDARD_ID);
        result.put("dentition", dentition);
        result.put("cariesRisk", cariesRisk);
        result.put("riskFactors", riskFactors);
        result.put("protectiveFactors", protectiveFactors);
        if (assessedAt != null) result.put("assessedAt", assessedAt);
        result.put("teeth", findings);
        return result;
    }

    private Map<String, Object> objectMap(Object value, String field) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?>)) fail("El campo " + field + " debe ser un objeto JSON");
        Map<?, ?> source = (Map<?, ?>) value;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }

    private List<String> textList(Object raw, String field) {
        if (raw == null) return List.of();
        if (!(raw instanceof Collection<?>)) fail("El campo " + field + " debe ser una lista");
        Collection<?> values = (Collection<?>) raw;
        if (values.size() > 20) fail("El campo " + field + " admite hasta 20 elementos");
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            String text = Objects.toString(value, "").trim();
            if (text.length() > 160) fail("Cada factor clínico admite hasta 160 caracteres");
            if (!text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private String optionalDate(Object raw) {
        String text = Objects.toString(raw, "").trim();
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text).toString();
        } catch (DateTimeParseException ex) {
            fail("La fecha de evaluación de riesgo debe usar el formato YYYY-MM-DD");
            return null;
        }
    }

    private void copyText(Map<String, Object> source, Map<String, Object> target, String key, int maxLength) {
        String value = Objects.toString(source.get(key), "").trim();
        if (value.length() > maxLength) fail("El campo " + key + " supera " + maxLength + " caracteres");
        if (!value.isBlank()) target.put(key, value);
    }

    private String canonicalToken(Object raw, String defaultValue) {
        String value = Objects.toString(raw, defaultValue).trim().toUpperCase(Locale.ROOT);
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el odontograma FDI", ex);
        }
    }

    private void fail(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
