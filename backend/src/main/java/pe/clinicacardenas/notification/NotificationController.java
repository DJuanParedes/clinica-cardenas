package pe.clinicacardenas.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.agenda.AgendaService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HU-23 / HU-24: bitácora, reenvío, preferencia de canal y webhook del microservicio. */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService service;
    private final NotificationDispatcher dispatcher;
    private final AgendaService agenda;
    private final PatientRepository patients;
    private final AuditService audit;
    private final String webhookToken;
    private final String panelUrl;

    public NotificationController(NotificationService service, NotificationDispatcher dispatcher, AgendaService agenda,
                                  PatientRepository patients, AuditService audit,
                                  @Value("${app.notifications.webhook-token}") String webhookToken,
                                  @Value("${app.notifications.panel-url:}") String panelUrl) {
        this.service = service;
        this.dispatcher = dispatcher;
        this.agenda = agenda;
        this.patients = patients;
        this.audit = audit;
        this.webhookToken = webhookToken;
        this.panelUrl = panelUrl;
    }

    public record ChannelRequest(String channel) {}
    public record Callback(String clave, String estado, String error, String proveedorId) {}

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('NOTIFICACIONES')")
    public Map<String, Object> list(@RequestParam(required = false) String status, @RequestParam(required = false) String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("counters", service.counters());
        m.put("items", service.latest(status, type, 300));
        m.put("panelUrl", panelUrl);
        return m;
    }

    @PostMapping("/notifications/{id}/retry")
    @PreAuthorize("hasAuthority('NOTIFICACIONES')")
    public Notification retry(@PathVariable Long id) {
        return service.retry(id);
    }

    @GetMapping("/patients/{id}/notifications")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    public List<Notification> forPatient(@PathVariable Long id) {
        return service.forPatient(id);
    }

    @PatchMapping("/patients/{id}/notification-channel")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    @Transactional
    public Patient channel(@PathVariable Long id, @RequestBody ChannelRequest req) {
        Patient p = patients.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
        String ch = req.channel() == null ? "" : req.channel().trim().toUpperCase();
        if (!NotificationService.CHANNEL_PREFS.contains(ch)) throw ApiException.field("channel", "Canal no válido.");
        if ("EMAIL".equals(ch) && (p.email == null || p.email.isBlank())) {
            throw ApiException.field("channel", "El paciente no tiene correo registrado. Agrégalo en sus datos primero.");
        }
        p.notifyChannel = ch;
        p.updatedAt = Dates.now();
        patients.save(p);
        audit.record("PACIENTE", p.id, "CANAL_AVISOS", "Avisos por " + ch.toLowerCase());
        return p;
    }

    @PostMapping("/appointments/{id}/remind")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    public Map<String, Object> remind(@PathVariable Long id) {
        var a = agenda.get(id);
        if (a.status.isFinal()) throw ApiException.badRequest("CITA_CERRADA", "La cita ya no está pendiente.");
        return service.remindNow(a);
    }

    /** Webhook del microservicio: resultado de un recordatorio programado. */
    @PostMapping("/internal/notifications/callback")
    public Map<String, Object> callback(@RequestHeader(value = "X-Webhook-Token", required = false) String token,
                                        @RequestBody Callback cb) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), webhookToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO", "Token del webhook inválido.");
        }
        if (cb.clave() == null) throw ApiException.field("clave", "Falta la clave del mensaje.");
        boolean found = dispatcher.applyCallback(cb.clave(), cb.estado(), cb.error(), cb.proveedorId());
        return Map.of("recibido", true, "encontrado", found);
    }
}
