package pe.clinicacardenas.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.security.CurrentUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v2.6: plantillas de mensajes, automatizaciones, historial de envíos y enlaces públicos. */
@RestController
@RequestMapping("/api/messaging")
public class MessagingController {
    private static final String PRERREGISTRO_ACTIVO = "prerregistro_activo";
    private static final String RESERVA_PUBLICA_ACTIVA = "reserva_publica_activa";


    private static final String ADMIN = "hasAuthority('AUTOMATIZACIONES')";
    private final MessageTemplateService templates;
    private final AutomationService automations;
    private final NotificationService notifications;
    private final SurveyLinks links;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public MessagingController(MessageTemplateService templates, AutomationService automations, NotificationService notifications,
                               SurveyLinks links, JdbcTemplate jdbc, AuditService audit) {
        this.templates = templates;
        this.automations = automations;
        this.notifications = notifications;
        this.links = links;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/templates")
    @PreAuthorize(ADMIN)
    public Map<String, Object> templates() {
        return Map.of("templates", templates.list(), "variables", MessageTemplateService.VARIABLES.keySet());
    }

    @PutMapping("/templates/{code}")
    @PreAuthorize(ADMIN)
    public Map<String, Object> update(@PathVariable String code, @RequestBody MessageTemplateService.TemplateRequest body) {
        Map<String, Object> t = new LinkedHashMap<>(templates.update(code, body));
        // los avisos ya programados de las citas futuras se rehacen con el texto nuevo
        if (usedByAutomation(code)) t.put("rescheduled", notifications.rescheduleFuture());
        return t;
    }

    @PostMapping("/templates/{code}/restore")
    @PreAuthorize(ADMIN)
    public Map<String, Object> restore(@PathVariable String code) {
        Map<String, Object> t = new LinkedHashMap<>(templates.restore(code));
        if (usedByAutomation(code)) t.put("rescheduled", notifications.rescheduleFuture());
        return t;
    }

    public record PreviewRequest(String subject, String body) {}

    @PostMapping("/templates/{code}/preview")
    @PreAuthorize(ADMIN)
    public Map<String, Object> preview(@PathVariable String code, @RequestBody PreviewRequest body) {
        return templates.preview(code, body.body(), body.subject());
    }

    @GetMapping("/automations")
    @PreAuthorize(ADMIN)
    public List<Map<String, Object>> automations() {
        return automations.view();
    }

    @PutMapping("/automations/{code}")
    @PreAuthorize(ADMIN)
    public Map<String, Object> updateAutomation(@PathVariable String code, @RequestBody AutomationService.RuleRequest body) {
        return automations.update(code, body);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('AUTOMATIZACIONES','NOTIFICACIONES')")
    public List<Map<String, Object>> history(@RequestParam(required = false) String template, @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String q, @RequestParam(defaultValue = "100") int limit) {
        return templates.history(template, status, q, limit);
    }

    @GetMapping("/public-links")
    @PreAuthorize(ADMIN)
    public Map<String, Object> publicLinks() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingUrl", links.bookingUrl());
        m.put("registerUrl", links.base() + "/registro");
        m.put("bookingEnabled", setting(RESERVA_PUBLICA_ACTIVA));
        m.put("registerEnabled", setting(PRERREGISTRO_ACTIVO));
        m.put("webBookings30d", jdbc.queryForObject("select count(*) from appointments where booked_via = 'WEB' and created_at >= ?", Integer.class, Dates.now().minusDays(30)));
        m.put("webPatients30d", jdbc.queryForObject("select count(*) from consent_records where channel = 'WEB' and accepted_at >= ?", Integer.class, Dates.now().minusDays(30)));
        return m;
    }

    public record LinksRequest(Boolean bookingEnabled, Boolean registerEnabled) {}

    @PutMapping("/public-links")
    @PreAuthorize(ADMIN)
    public Map<String, Object> updateLinks(@RequestBody LinksRequest r) {
        if (r.bookingEnabled() != null) put(RESERVA_PUBLICA_ACTIVA, r.bookingEnabled());
        if (r.registerEnabled() != null) put(PRERREGISTRO_ACTIVO, r.registerEnabled());
        audit.record("CONFIGURACION", null, "ENLACES", "Reserva web " + (setting(RESERVA_PUBLICA_ACTIVA) ? "activa" : "pausada")
                + " · prerregistro " + (setting(PRERREGISTRO_ACTIVO) ? "activo" : "pausado"));
        return publicLinks();
    }

    private boolean usedByAutomation(String code) {
        Integer n = jdbc.queryForObject("select count(*) from automation_rules where template_code = ? and active = true and kind <> 'DESPUES'", Integer.class, code);
        return n != null && n > 0;
    }

    private boolean setting(String k) {
        return jdbc.queryForList("select v from app_settings where k = ?", String.class, k).stream().findFirst().map("true"::equals).orElse(false);
    }

    private void put(String k, boolean v) {
        int n = jdbc.update("update app_settings set v = ?, updated_at = ?, updated_by = ? where k = ?", String.valueOf(v), Dates.now(), CurrentUser.usernameOrSystem(), k);
        if (n == 0) jdbc.update("insert into app_settings (k, v, updated_at, updated_by) values (?,?,?,?)", k, String.valueOf(v), Dates.now(), CurrentUser.usernameOrSystem());
    }
}
