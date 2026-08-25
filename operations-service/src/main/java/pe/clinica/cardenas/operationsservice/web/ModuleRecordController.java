package pe.clinica.cardenas.operationsservice.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.operationsservice.domain.ModuleRecord;
import pe.clinica.cardenas.operationsservice.repository.ModuleRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleRecordController {
    private static final Set<String> STANDARD_FIELDS = Set.of(
            "id", "moduleKey", "title", "subject", "detail", "status", "responsible",
            "eventDate", "amount", "createdAt", "updatedAt", "payloadJson");

    private final ModuleRecordRepository repository;
    private final ObjectMapper objectMapper;


    private static final Map<String, List<String>> STATUS_OPTIONS = Map.ofEntries(
            Map.entry("clinical-history", List.of("ACTIVA", "EN REVISIÓN", "CERRADA")),
            Map.entry("treatments", List.of("PROPUESTO", "ACEPTADO", "EN PROCESO", "COMPLETADO", "CANCELADO")),
            Map.entry("prescriptions", List.of("VIGENTE", "ANULADA", "REEMPLAZADA")),
            Map.entry("inventory", List.of("ACTIVO", "INACTIVO", "AGOTADO")),
            Map.entry("sterilization", List.of("PENDIENTE", "EN PROCESO", "CONFORME", "OBSERVADO", "RECHAZADO")),
            Map.entry("security", List.of("PENDIENTE", "REVISADO", "MITIGADO", "CERRADO")),
            Map.entry("monitoring", List.of("SALUDABLE", "ADVERTENCIA", "CRÍTICO", "RESUELTO")),
            Map.entry("backups", List.of("PROGRAMADO", "EN PROCESO", "COMPLETADO", "FALLIDO", "RESTAURADO")),
            Map.entry("integrations", List.of("BORRADOR", "CONFIGURADO", "EN PRUEBAS", "ACTIVO", "SUSPENDIDO")),
            Map.entry("environments", List.of("ACTIVO", "DETENIDO", "EN DESPLIEGUE", "DEGRADADO")),
            Map.entry("quality", List.of("PENDIENTE", "EN EJECUCIÓN", "APROBADO", "OBSERVADO", "CERRADO")),
            Map.entry("migration", List.of("BORRADOR", "EN REVISIÓN", "VALIDADO", "IMPORTADO", "OBSERVADO")),
            Map.entry("settings", List.of("ACTIVO", "INACTIVO", "PENDIENTE"))
    );

    private static final Map<String, Set<String>> READ_ROLES = Map.ofEntries(
            Map.entry("clinical-history", Set.of("ADMIN", "DOCTOR")),
            Map.entry("treatments", Set.of("ADMIN", "DOCTOR", "FINANZAS")),
            Map.entry("prescriptions", Set.of("ADMIN", "DOCTOR")),
            Map.entry("inventory", Set.of("ADMIN", "DOCTOR", "RECEPCION", "FINANZAS")),
            Map.entry("sterilization", Set.of("ADMIN", "DOCTOR")),
            Map.entry("security", Set.of("ADMIN")),
            Map.entry("monitoring", Set.of("ADMIN")),
            Map.entry("backups", Set.of("ADMIN")),
            Map.entry("integrations", Set.of("ADMIN")),
            Map.entry("environments", Set.of("ADMIN")),
            Map.entry("quality", Set.of("ADMIN")),
            Map.entry("migration", Set.of("ADMIN")),
            Map.entry("settings", Set.of("ADMIN"))
    );

    private static final Map<String, Set<String>> WRITE_ROLES = Map.ofEntries(
            Map.entry("clinical-history", Set.of("ADMIN", "DOCTOR")),
            Map.entry("treatments", Set.of("ADMIN", "DOCTOR")),
            Map.entry("prescriptions", Set.of("ADMIN", "DOCTOR")),
            Map.entry("inventory", Set.of("ADMIN", "RECEPCION", "FINANZAS")),
            Map.entry("sterilization", Set.of("ADMIN")),
            Map.entry("security", Set.of("ADMIN")),
            Map.entry("monitoring", Set.of("ADMIN")),
            Map.entry("backups", Set.of("ADMIN")),
            Map.entry("integrations", Set.of("ADMIN")),
            Map.entry("environments", Set.of("ADMIN")),
            Map.entry("quality", Set.of("ADMIN")),
            Map.entry("migration", Set.of("ADMIN")),
            Map.entry("settings", Set.of("ADMIN"))
    );

    @GetMapping("/{moduleKey}")
    public List<Map<String, Object>> list(@PathVariable String moduleKey, Authentication authentication) {
        authorize(moduleKey, authentication, false);
        return repository.findAllByModuleKeyOrderByUpdatedAtDesc(moduleKey).stream().map(this::view).toList();
    }

    @PostMapping("/{moduleKey}")
    public Map<String, Object> create(@PathVariable String moduleKey, @RequestBody Map<String, Object> request,
                                      Authentication authentication) {
        authorize(moduleKey, authentication, true);
        ModuleRecord item = new ModuleRecord();
        item.setModuleKey(moduleKey);
        apply(item, request, true);
        LocalDateTime now = LocalDateTime.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return view(repository.save(item));
    }

    @PutMapping("/{moduleKey}/{id}")
    public Map<String, Object> update(@PathVariable String moduleKey, @PathVariable Long id,
                                      @RequestBody Map<String, Object> request, Authentication authentication) {
        authorize(moduleKey, authentication, true);
        ModuleRecord current = find(moduleKey, id);
        apply(current, request, false);
        current.setUpdatedAt(LocalDateTime.now());
        return view(repository.save(current));
    }

    @PatchMapping("/{moduleKey}/{id}/status")
    public Map<String, Object> status(@PathVariable String moduleKey, @PathVariable Long id,
                                      @RequestParam String value, Authentication authentication) {
        authorize(moduleKey, authentication, true);
        ModuleRecord current = find(moduleKey, id);
        current.setStatus(validStatus(moduleKey, value));
        current.setUpdatedAt(LocalDateTime.now());
        return view(repository.save(current));
    }

    @DeleteMapping("/{moduleKey}/{id}")
    public void delete(@PathVariable String moduleKey, @PathVariable Long id, Authentication authentication) {
        authorize(moduleKey, authentication, true);
        repository.delete(find(moduleKey, id));
    }

    private void apply(ModuleRecord item, Map<String, Object> request, boolean creating) {
        String title = text(request.get("title"));
        if (title == null || title.isBlank()) {
            title = firstNonBlank(text(request.get("patientName")), text(request.get("name")),
                    text(request.get("serviceName")), text(request.get("subject")), "Registro");
        }
        item.setTitle(title);
        item.setSubject(text(request.get("subject")));
        item.setDetail(text(request.get("detail")));
        item.setResponsible(text(request.get("responsible")));
        item.setEventDate(date(request.get("eventDate")));
        item.setAmount(decimal(request.get("amount")));
        String status = text(request.get("status"));
        if (status != null && !status.isBlank()) item.setStatus(validStatus(item.getModuleKey(), status));
        else if (creating) item.setStatus(defaultStatus(item.getModuleKey()));

        Map<String, Object> payload = new LinkedHashMap<>();
        request.forEach((key, value) -> {
            if (!STANDARD_FIELDS.contains(key)) payload.put(key, value);
        });
        try {
            item.setPayloadJson(payload.isEmpty() ? null : objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo procesar la información adicional");
        }
    }

    private Map<String, Object> view(ModuleRecord item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("moduleKey", item.getModuleKey());
        result.put("title", item.getTitle());
        result.put("subject", item.getSubject());
        result.put("detail", item.getDetail());
        result.put("status", item.getStatus());
        result.put("responsible", item.getResponsible());
        result.put("eventDate", item.getEventDate());
        result.put("amount", item.getAmount());
        result.put("createdAt", item.getCreatedAt());
        result.put("updatedAt", item.getUpdatedAt());
        if (item.getPayloadJson() != null && !item.getPayloadJson().isBlank()) {
            try {
                Map<String, Object> payload = objectMapper.readValue(item.getPayloadJson(), new TypeReference<>() {});
                result.putAll(payload);
            } catch (Exception ignored) {
                result.put("payloadError", true);
            }
        }
        return result;
    }

    private ModuleRecord find(String moduleKey, Long id) {
        ModuleRecord item = repository.findById(id).orElseThrow(NoSuchElementException::new);
        if (!moduleKey.equals(item.getModuleKey())) throw new NoSuchElementException();
        return item;
    }

    private void authorize(String moduleKey, Authentication authentication, boolean write) {
        Set<String> roles = (write ? WRITE_ROLES : READ_ROLES).get(moduleKey);
        if (roles == null) throw new IllegalArgumentException("Módulo no reconocido");
        String role = authentication.getAuthorities().stream().findFirst().map(Object::toString)
                .orElse("").replace("ROLE_", "");
        if (!roles.contains(role)) throw new org.springframework.security.access.AccessDeniedException("Acceso denegado");
    }


    private String defaultStatus(String moduleKey) {
        List<String> allowed = STATUS_OPTIONS.get(moduleKey);
        if (allowed == null || allowed.isEmpty()) throw new IllegalArgumentException("Módulo sin estados configurados");
        return allowed.get(0);
    }

    private String validStatus(String moduleKey, String value) {
        String requested = value == null ? "" : value.trim();
        return STATUS_OPTIONS.getOrDefault(moduleKey, List.of()).stream()
                .filter(option -> option.equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado no permitido para " + moduleKey));
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private LocalDate date(Object value) {
        String text = text(value);
        return text == null || text.isBlank() ? null : LocalDate.parse(text.substring(0, Math.min(10, text.length())));
    }
    private BigDecimal decimal(Object value) {
        String text = text(value);
        return text == null || text.isBlank() ? null : new BigDecimal(text);
    }
    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).filter(v -> !v.isBlank()).findFirst().orElse("Registro");
    }
}
