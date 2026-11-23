package pe.clinicacardenas.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HU-23 recordatorio de cita · HU-24 aviso cuando cambia la cita.
 *
 * Las notificaciones se guardan en la misma transacción que la cita (bandeja de salida). Al confirmar la
 * transacción se envían al microservicio de notificaciones; si no responde, se reintentan con espera creciente.
 * Cada mensaje lleva una clave de idempotencia: el microservicio no envía dos veces el mismo mensaje.
 */
@Service
public class NotificationService {

    public static final List<String> CHANNEL_PREFS = List.of("WHATSAPP", "EMAIL", "AMBOS", "NINGUNO");
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", ES);
    private static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final NotificationRepository repo;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final String clinicName;
    private final String clinicAddress;
    private final String clinicPhone;

    private final pe.clinicacardenas.messaging.MessageTemplateService templates;
    private final pe.clinicacardenas.messaging.AutomationService automations;
    private final pe.clinicacardenas.messaging.SurveyLinks links;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public NotificationService(NotificationRepository repo, ApplicationEventPublisher events, AuditService audit,
                               @Value("${app.company.trade-name}") String clinicName,
                               @Value("${app.company.address}") String clinicAddress,
                               @Value("${app.notifications.clinic-phone:987 654 000}") String clinicPhone,
                               pe.clinicacardenas.messaging.MessageTemplateService templates,
                               @org.springframework.context.annotation.Lazy pe.clinicacardenas.messaging.AutomationService automations,
                               pe.clinicacardenas.messaging.SurveyLinks links,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.templates = templates;
        this.automations = automations;
        this.links = links;
        this.jdbc = jdbc;
        this.repo = repo;
        this.events = events;
        this.audit = audit;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicPhone = clinicPhone;
    }

    /** Evento que dispara el envío inmediato una vez confirmada la transacción. */
    public record Queued() {}

    public record Target(String channel, String destination) {}

    public static List<Target> targets(Patient p) {
        String pref = p.notifyChannel == null ? "WHATSAPP" : p.notifyChannel;
        List<Target> t = new ArrayList<>();
        boolean hasEmail = p.email != null && !p.email.isBlank();
        boolean hasPhone = p.phone != null && !p.phone.isBlank();
        switch (pref) {
            case "NINGUNO" -> { }
            case "EMAIL" -> {
                if (hasEmail) t.add(new Target("EMAIL", p.email));
                else if (hasPhone) t.add(new Target("WHATSAPP", p.phone)); // sin correo: se usa WhatsApp
            }
            case "AMBOS" -> {
                if (hasPhone) t.add(new Target("WHATSAPP", p.phone));
                if (hasEmail) t.add(new Target("EMAIL", p.email));
            }
            default -> { if (hasPhone) t.add(new Target("WHATSAPP", p.phone)); }
        }
        return t;
    }

    private static String firstName(Patient p) {
        return p.firstName.split(" ")[0];
    }

    private String when(LocalDateTime t) {
        String d = t.format(DAY);
        return d.substring(0, 1).toUpperCase(ES) + d.substring(1) + " a las " + t.format(HOUR);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static String dentistText(Appointment a) {
        return a.dentist.getFullName() + (a.dentist.specialty == null || a.dentist.specialty.isBlank() ? "" : " (" + a.dentist.specialty + ")");
    }

    private String body(String channel, String intro, Appointment a, String closing) {
        String dentist = a.dentist.getFullName() + " (" + a.dentist.specialty + ")";
        if ("WHATSAPP".equals(channel)) {
            return intro + "\n\n📅 " + when(a.startAt) + "\n🦷 " + dentist + "\n📍 " + clinicAddress
                    + (closing == null ? "" : "\n\n" + closing) + "\n\n" + clinicName + " · ¡Que tu sonrisa sea para toda la vida!";
        }
        return intro + "\n\nFecha: " + when(a.startAt) + "\nOdontólogo: " + dentist + "\nMotivo: " + a.reason
                + "\nDirección: " + clinicAddress + (closing == null ? "" : "\n\n" + closing)
                + "\n\nAtentamente,\n" + clinicName + "\nTeléfono / WhatsApp: " + clinicPhone;
    }

    /** Por WhatsApp el paciente puede responder 1 o 2 (lo procesa el webhook del microservicio). */
    private static String replyHint(String channel) {
        return "WHATSAPP".equals(channel)
                ? "Responde *1* para confirmar tu asistencia o *2* si necesitas reprogramar."
                : "Si no puedes asistir, avísanos respondiendo este mensaje.";
    }

    /**
     * Mensaje libre a un paciente por sus canales preferidos (recordatorios de control, lista de espera, cuotas).
     * La clave evita enviar dos veces el mismo aviso.
     */
    @Transactional
    public int message(Patient p, String type, String subject, String text, String key) {
        int n = 0;
        for (Target t : targets(p)) {
            String k = key + ":" + t.channel();
            if (repo.existsByIdempotencyKey(k)) continue;
            add(p, null, type, "ENVIAR", t, subject, "Hola " + firstName(p) + ", " + text
                    + ("WHATSAPP".equals(t.channel()) ? "\n\n" + clinicName : "\n\nAtentamente,\n" + clinicName + "\nTeléfono / WhatsApp: " + clinicPhone), k, null);
            n++;
        }
        return n;
    }

    private void add(Patient p, Long appointmentId, String type, String operation, Target t, String subject, String body,
                     String key, LocalDateTime scheduledFor) {
        add(p, appointmentId, type, operation, t, subject, body, key, scheduledFor, null);
    }

    /** Plantilla de WhatsApp con formato (saltos de línea, negritas) y sus parámetros, en orden {{1}}, {{2}}… */
    private record Wa(String template, List<String> params) {}

    /** v2.9: plantillas de WhatsApp que la clínica debe tener aprobadas en Meta (ver docs/PLANTILLAS_WHATSAPP.md). */
    public static final String WA_PROMOCION = "novedad_clinica";
    public static final String WA_CUMPLEANOS = "cumpleanos_clinica";
    public static final String WA_DOCUMENTO = "documento_por_firmar";

    /** Texto libre como parámetro de plantilla: una sola línea, sin el pie de baja (ya está en la plantilla), máx. 700. */
    private static String waText(String s) {
        String t = s == null ? "" : s.replaceAll("[ \\t]*+\\r?+\\n[ \\t\\r\\n]*+", " · ").replaceAll(" {2,}+", " ").trim();
        return t.length() > 700 ? t.substring(0, 699) + "…" : t;
    }

    private Wa wa(String template, String... params) {
        return new Wa(template, List.of(params));
    }

    /**
     * v2.10: horario de silencio (21:00 a 08:00). Lo programado de noche se corre a las 08:00; si es un aviso previo a
     * una cita y a las 08:00 ya sería tarde, se adelanta a las 20:00 de la noche anterior.
     */
    static final java.time.LocalTime QUIET_FROM = java.time.LocalTime.of(21, 0), QUIET_TO = java.time.LocalTime.of(8, 0);

    LocalDateTime quietHours(LocalDateTime at, Long appointmentId) {
        if (at == null) return null;
        java.time.LocalTime t = at.toLocalTime();
        if (!t.isBefore(QUIET_TO) && t.isBefore(QUIET_FROM)) return at;
        LocalDateTime morning = (t.isBefore(QUIET_TO) ? at.toLocalDate() : at.toLocalDate().plusDays(1)).atTime(QUIET_TO);
        if (appointmentId != null) {
            java.util.List<LocalDateTime> start = jdbc.queryForList("select start_at from appointments where id = ?", LocalDateTime.class, appointmentId);
            if (!start.isEmpty() && at.isBefore(start.get(0)) && !morning.isBefore(start.get(0).minusMinutes(60))) {
                LocalDateTime evening = (t.isBefore(QUIET_TO) ? at.toLocalDate().minusDays(1) : at.toLocalDate()).atTime(20, 0);
                return evening.isAfter(Dates.now()) ? evening : at;
            }
        }
        return morning;
    }

    private void add(Patient p, Long appointmentId, String type, String operation, Target t, String subject, String body,
                     String key, LocalDateTime scheduledFor, Wa wa) {
        if (repo.existsByIdempotencyKey(key)) return; // ya encolado: no se duplica
        Notification n = new Notification();
        n.patientId = p.id;
        n.appointmentId = appointmentId;
        n.type = type;
        n.operation = operation;
        n.channel = t.channel();
        n.destination = t.destination();
        n.subject = subject;
        n.body = body;
        n.idempotencyKey = key;
        n.scheduledFor = quietHours(scheduledFor, appointmentId);
        if (wa != null && "WHATSAPP".equals(t.channel())) {
            n.waTemplate = wa.template();
            try {
                n.waParams = JSON.writeValueAsString(wa.params());
            } catch (Exception e) {
                n.waTemplate = null;
            }
        }
        n.status = "PENDIENTE";
        n.createdAt = Dates.now();
        repo.save(n);
        events.publishEvent(new Queued());
    }

    // ------------------------------------------------------------------ v2.6: plantillas editables

    /** Plantilla que reemplaza a "Cita registrada" en esta petición (p. ej. RESERVA_WEB desde el enlace público). */
    private static final ThreadLocal<String> CREATED_TEMPLATE = new ThreadLocal<>();

    public static <T> T withCreatedTemplate(String code, java.util.function.Supplier<T> action) {
        CREATED_TEMPLATE.set(code);
        try { return action.get(); } finally { CREATED_TEMPLATE.remove(); }
    }

    /** v2.7: variables que no dependen de una cita (campañas y cumpleaños). */
    public Map<String, String> patientVars(Patient p) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("paciente", firstName(p));
        v.put("paciente_completo", p.getFullName());
        v.put("clinica", clinicName);
        v.put("direccion", clinicAddress);
        v.put("telefono_clinica", clinicPhone);
        v.put("enlace_citas", links.bookingUrl());
        return v;
    }

    public static final List<String> PATIENT_VARS = List.of("paciente", "paciente_completo", "clinica", "direccion", "telefono_clinica", "enlace_citas");

    /** Destinos según el canal de la campaña: PREFERIDO (el del paciente), WHATSAPP o EMAIL. */
    public static List<Target> targets(Patient p, String channel) {
        if (channel == null || "PREFERIDO".equals(channel)) return targets(p);
        if ("NINGUNO".equals(p.notifyChannel)) return List.of();
        if ("WHATSAPP".equals(channel)) return p.phone == null || p.phone.isBlank() ? List.of() : List.of(new Target("WHATSAPP", p.phone));
        if ("EMAIL".equals(channel)) return p.email == null || p.email.isBlank() ? List.of() : List.of(new Target("EMAIL", p.email));
        return List.of();
    }

    public record Sent(String channel, Long notificationId) {}

    /**
     * v2.7: mensaje de marketing (campaña o cumpleaños) con su texto ya escrito y variables del paciente.
     * Lleva al final cómo darse de baja. La clave evita repetir el envío. Devuelve lo que quedó en cola.
     */
    @Transactional
    public List<Sent> marketing(Patient p, String type, String templateCode, String subject, String body, String channel, String key) {
        Map<String, String> v = patientVars(p);
        String subj = pe.clinicacardenas.messaging.MessageTemplateService.fill(subject, v);
        String text = pe.clinicacardenas.messaging.MessageTemplateService.fill(body, v);
        List<Sent> out = new ArrayList<>();
        for (Target t : targets(p, channel)) {
            String k = key + ":" + t.channel();
            if (repo.existsByIdempotencyKey(k)) continue;
            String b = "WHATSAPP".equals(t.channel())
                    ? text + "\n\n_Responde *BAJA* si no deseas recibir más promociones._"
                    : text.replace("*", "") + "\n\nAtentamente,\n" + clinicName + "\nTeléfono / WhatsApp: " + clinicPhone
                      + "\n\nSi no deseas recibir más promociones, respóndenos BAJA por WhatsApp o avísanos en recepción.";
            // v2.9: fuera de la ventana de 24 h Meta solo acepta plantillas aprobadas de categoría marketing.
            Wa meta = "CUMPLEANOS".equals(type) ? wa(WA_CUMPLEANOS, clinicName, waText(text))
                    : wa(WA_PROMOCION, clinicName, waText(text));
            add(p, null, type, "ENVIAR", t, subj, b, k, null, meta);
            repo.findByIdempotencyKey(k).ifPresent(n -> {
                n.templateCode = templateCode;
                repo.save(n);
                out.add(new Sent(t.channel(), n.id));
            });
        }
        return out;
    }

    /**
     * v2.8: mensaje directo a un paciente por un canal concreto (p. ej. el enlace para firmar un consentimiento).
     * No es publicidad: no lleva pie de baja. Devuelve lo que quedó en cola.
     */
    @Transactional
    public List<Sent> direct(Patient p, String type, String channel, String destination, String subject, String body, String key) {
        return direct(p, type, channel, destination, subject, body, key, null);
    }

    /** Igual que {@link #direct}, con la plantilla de WhatsApp aprobada y sus parámetros (nombre, p1, p2, ...). */
    @Transactional
    public List<Sent> direct(Patient p, String type, String channel, String destination, String subject, String body, String key,
                             List<String> waTemplate) {
        List<Sent> out = new ArrayList<>();
        Target t = new Target(channel, destination);
        String k = key + ":" + channel;
        if (repo.existsByIdempotencyKey(k)) return out;
        String b = "WHATSAPP".equals(channel) ? body : body.replace("*", "") + "\n\nAtentamente,\n" + clinicName + "\nTeléfono / WhatsApp: " + clinicPhone;
        Wa meta = waTemplate == null || waTemplate.isEmpty() ? null : new Wa(waTemplate.get(0), waTemplate.subList(1, waTemplate.size()));
        add(p, null, type, "ENVIAR", t, subject, b, k, null, meta);
        repo.findByIdempotencyKey(k).ifPresent(n -> out.add(new Sent(channel, n.id)));
        return out;
    }

    /** Variables de la plantilla para una cita. */
    public Map<String, String> vars(Appointment a) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("paciente", firstName(a.patient));
        v.put("paciente_completo", a.patient.getFullName());
        String d = a.startAt.format(DAY);
        v.put("fecha", d.substring(0, 1).toUpperCase(ES) + d.substring(1));
        v.put("hora", a.startAt.format(HOUR));
        v.put("fecha_hora", when(a.startAt));
        v.put("odontologo", dentistText(a));
        v.put("especialidad", a.dentist.specialty == null ? "" : a.dentist.specialty);
        v.put("motivo", a.reason == null ? "" : a.reason);
        v.put("clinica", clinicName);
        v.put("direccion", clinicAddress);
        v.put("telefono_clinica", clinicPhone);
        v.put("enlace_citas", links.bookingUrl());
        v.put("enlace_encuesta", links.surveyUrl(a.id));
        return v;
    }

    /** Arma el aviso desde la plantilla. En WhatsApp real se usa la plantilla aprobada por Meta solo si el texto no se editó. */
    private void addFromTemplate(Patient p, Long appointmentId, String type, String operation, Target t, String templateCode,
                                 Map<String, String> vars, String key, LocalDateTime scheduledFor, Wa metaTemplate) {
        var r = templates.render(templateCode, vars);
        String body = "WHATSAPP".equals(t.channel()) ? r.body()
                : r.body().replace("*", "") + "\n\nAtentamente,\n" + clinicName + "\nTeléfono / WhatsApp: " + clinicPhone;
        if (repo.existsByIdempotencyKey(key)) return;
        add(p, appointmentId, type, operation, t, r.subject(), body, key, scheduledFor, r.customized() ? null : metaTemplate);
        repo.findByIdempotencyKey(key).ifPresent(n -> {
            n.templateCode = templateCode;
            if ("WHATSAPP".equals(t.channel()) && !r.buttons().isEmpty()) {
                try { n.waButtons = JSON.writeValueAsString(r.buttons()); } catch (Exception ignored) { }
            }
            repo.save(n);
        });
    }

    /** Programa en el microservicio los avisos "antes" y "el día" de la cita según las automatizaciones activas. */
    private void scheduleAutomations(Appointment a) {
        LocalDateTime now = Dates.now();
        for (var rule : automations.rules()) {
            if (!rule.active() || "DESPUES".equals(rule.kind())) continue;
            LocalDateTime at = rule.when(a.startAt, a.endAt, now);
            if (at == null) continue;
            for (Target t : targets(a.patient)) {
                String base = "AUTO_1D".equals(rule.code()) ? reminderKey(a, t.channel())
                        : "auto:" + rule.code() + ":" + a.id + ":" + a.startAt.format(KEY) + ":" + t.channel();
                // si ese aviso se anuló (regla o texto editados), se programa de nuevo con otra clave
                String key = base;
                for (int i = 2; repo.findByIdempotencyKey(key).map(x -> "ANULADO".equals(x.status)).orElse(false); i++) key = base + ":r" + i;
                Wa meta = "AUTO_1D".equals(rule.code()) ? wa("recordatorio_cita", firstName(a.patient), when(a.startAt), dentistText(a), clinicAddress) : null;
                addFromTemplate(a.patient, a.id, rule.notifType(), "PROGRAMAR", t, rule.templateCode(), vars(a), key, at, meta);
            }
        }
    }

    /** Al quedar atendida: agradecimiento y encuesta según las automatizaciones "después". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentAttended(Appointment a) {
        LocalDateTime now = Dates.now();
        for (var rule : automations.rules()) {
            if (!rule.active() || !"DESPUES".equals(rule.kind())) continue;
            LocalDateTime at = rule.when(a.startAt, a.endAt, now);
            if (at == null) continue;
            boolean immediate = !at.isAfter(now.plusMinutes(5));
            for (Target t : targets(a.patient)) {
                addFromTemplate(a.patient, a.id, rule.notifType(), immediate ? "ENVIAR" : "PROGRAMAR", t, rule.templateCode(), vars(a),
                        "auto:" + rule.code() + ":" + a.id + ":" + t.channel(), immediate ? null : at, null);
            }
        }
    }

    /**
     * Tras editar una automatización o una plantilla: se vuelven a programar los avisos de las citas futuras (60 días)
     * con el horario y el texto nuevos. Devuelve cuántas citas se reprogramaron.
     */
    @Transactional
    public int rescheduleFuture() {
        List<Long> ids = jdbc.queryForList("select id from appointments where status in ('PROGRAMADA','CONFIRMADA') and start_at > ? and start_at < ?",
                Long.class, Dates.now(), Dates.now().plusDays(60));
        int n = 0;
        for (Long id : ids) {
            Appointment a = appointmentLoader.apply(id);
            if (a == null) continue;
            unscheduleReminders(a);
            scheduleAutomations(a);
            n++;
        }
        return n;
    }

    private java.util.function.Function<Long, Appointment> appointmentLoader = id -> null;

    @org.springframework.beans.factory.annotation.Autowired
    void setAppointmentLoader(@org.springframework.context.annotation.Lazy pe.clinicacardenas.agenda.AppointmentRepository appts) {
        this.appointmentLoader = id -> appts.findById(id).orElse(null);
    }

    /** Momento del recordatorio: 24 h antes; si la cita es antes de eso, 2 h antes; si ya no da tiempo, ninguno. */
    public static LocalDateTime reminderTime(LocalDateTime start, LocalDateTime now) {
        LocalDateTime d24 = start.minusHours(24);
        if (d24.isAfter(now.plusMinutes(5))) return d24;
        LocalDateTime d2 = start.minusHours(2);
        if (d2.isAfter(now.plusMinutes(5))) return d2;
        return null;
    }

    private String reminderKey(Appointment a, String channel) {
        return "recordatorio:" + a.id + ":" + a.startAt.format(KEY) + ":" + channel;
    }

    private void scheduleReminder(Appointment a) {
        scheduleAutomations(a);
    }

    /** Anula los recordatorios programados de la cita (al reprogramarla o cancelarla). */
    private void unscheduleReminders(Appointment a) {
        for (Notification old : repo.findByAppointmentIdAndOperationAndStatusIn(a.id, "PROGRAMAR", List.of("PENDIENTE", "PROGRAMADO", "FALLIDO"))) {
            boolean neverReached = "PENDIENTE".equals(old.status) || "FALLIDO".equals(old.status);
            old.status = "ANULADO";
            old.updatedAt = Dates.now();
            repo.save(old);
            if (!neverReached) {
                Notification cancel = new Notification();
                cancel.patientId = old.patientId;
                cancel.appointmentId = old.appointmentId;
                cancel.type = "RECORDATORIO";
                cancel.operation = "ANULAR";
                cancel.channel = old.channel;
                cancel.destination = old.destination;
                cancel.subject = "Anular recordatorio";
                cancel.body = "Se anula el recordatorio " + old.idempotencyKey;
                cancel.idempotencyKey = "anular:" + old.idempotencyKey;
                cancel.status = "PENDIENTE";
                cancel.createdAt = Dates.now();
                if (!repo.existsByIdempotencyKey(cancel.idempotencyKey)) repo.save(cancel);
            }
        }
        events.publishEvent(new Queued());
    }

    // ------------------------------------------------------------------ eventos de la agenda

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentCreated(Appointment a) {
        String code = CREATED_TEMPLATE.get() == null ? "CITA_REGISTRADA" : CREATED_TEMPLATE.get();
        for (Target t : targets(a.patient)) {
            addFromTemplate(a.patient, a.id, "CITA_REGISTRADA", "ENVIAR", t, code, vars(a), "registro:" + a.id + ":" + t.channel(), null,
                    "CITA_REGISTRADA".equals(code) ? wa("cita_registrada", firstName(a.patient), when(a.startAt), dentistText(a), clinicAddress) : null);
        }
        scheduleReminder(a);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentRescheduled(Appointment a, LocalDateTime previousStart, String previousDentist) {
        unscheduleReminders(a);
        Map<String, String> v = vars(a);
        v.put("fecha_anterior", when(previousStart) + (previousDentist.equals(a.dentist.getFullName()) ? "" : " con " + previousDentist));
        for (Target t : targets(a.patient)) {
            addFromTemplate(a.patient, a.id, "CAMBIO_CITA", "ENVIAR", t, "CAMBIO_CITA", v,
                    "cambio:" + a.id + ":" + a.startAt.format(KEY) + ":" + t.channel(), null,
                    wa("cita_reprogramada", firstName(a.patient), when(previousStart), when(a.startAt), dentistText(a), clinicAddress));
        }
        scheduleReminder(a);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentCancelled(Appointment a, String reason) {
        unscheduleReminders(a);
        Map<String, String> v = vars(a);
        v.put("motivo_cancelacion", reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")");
        for (Target t : targets(a.patient)) {
            addFromTemplate(a.patient, a.id, "CANCELACION", "ENVIAR", t, "CANCELACION", v, "cancelacion:" + a.id + ":" + t.channel(), null,
                    wa("cita_cancelada", firstName(a.patient), when(a.startAt), a.dentist.getFullName(), clinicPhone));
        }
    }

    /** Recordatorio inmediato solicitado por recepción. La clave evita enviarlo dos veces para la misma cita y hora. */
    @Transactional
    public Map<String, Object> remindNow(Appointment a) {
        List<Target> ts = targets(a.patient);
        if (ts.isEmpty()) {
            throw ApiException.badRequest("SIN_CANAL", "El paciente no tiene un canal de notificación activo (preferencia o datos de contacto).");
        }
        int queued = 0, duplicated = 0;
        for (Target t : ts) {
            String key = "recordatorio-manual:" + a.id + ":" + a.startAt.format(KEY) + ":" + t.channel();
            if (repo.existsByIdempotencyKey(key)) { duplicated++; continue; }
            addFromTemplate(a.patient, a.id, "RECORDATORIO", "ENVIAR", t, "RECORDATORIO", vars(a), key, null,
                    wa("recordatorio_cita", firstName(a.patient), when(a.startAt), dentistText(a), clinicAddress));
            queued++;
        }
        audit.record("CITA", a.id, "RECORDATORIO", queued > 0 ? "Recordatorio enviado por " + ts.stream().map(Target::channel).toList()
                : "Recordatorio no reenviado: ya se había enviado");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queued", queued);
        m.put("duplicated", duplicated);
        m.put("message", queued > 0 ? "Recordatorio enviado al paciente." : "Este recordatorio ya se envió antes; no se duplicó.");
        return m;
    }

    /** HU-22: código para activar la cuenta de la aplicación del paciente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void accessCode(Patient p, String code) {
        Target t = p.phone != null && !p.phone.isBlank() ? new Target("WHATSAPP", p.phone)
                : p.email != null ? new Target("EMAIL", p.email) : null;
        if (t == null) return;
        String text = "Hola " + firstName(p) + ", tu código para activar la app de " + clinicName + " es: " + code
                + "\nVence en 15 minutos. Si no lo pediste, ignora este mensaje.";
        add(p, null, "CODIGO_ACCESO", "ENVIAR", t, "Código de acceso · " + clinicName, text,
                "codigo:" + p.id + ":" + System.currentTimeMillis(), null);
    }

    // ------------------------------------------------------------------ consulta y reenvío

    public List<Notification> latest(String status, String type, int size) {
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(size, 500))).stream()
                .filter(n -> status == null || status.isBlank() || n.status.equals(status))
                .filter(n -> type == null || type.isBlank() || n.type.equals(type))
                .toList();
    }

    public List<Notification> forPatient(Long patientId) {
        return repo.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    public Map<String, Long> counters() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : List.of("PENDIENTE", "PROGRAMADO", "ENVIADO", "ENTREGADO", "LEIDO", "FALLIDO", "DUPLICADO", "ANULADO")) m.put(s, repo.countByStatus(s));
        return m;
    }

    /** Reenvío manual de un mensaje fallido: nueva clave, para que sea un envío intencional y no un duplicado. */
    @Transactional
    public Notification retry(Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("La notificación"));
        if (!"FALLIDO".equals(n.status)) throw ApiException.conflict("NO_REENVIABLE", "Solo se reenvían notificaciones fallidas.");
        if ("ANULAR".equals(n.operation)) throw ApiException.conflict("NO_REENVIABLE", "Esta operación no se reenvía.");
        Notification copy = new Notification();
        copy.patientId = n.patientId;
        copy.appointmentId = n.appointmentId;
        copy.type = n.type;
        copy.operation = "ENVIAR";
        copy.channel = n.channel;
        copy.destination = n.destination;
        copy.subject = n.subject;
        copy.body = n.body;
        copy.idempotencyKey = n.idempotencyKey + ":reenvio:" + System.currentTimeMillis();
        copy.status = "PENDIENTE";
        copy.createdAt = Dates.now();
        repo.save(copy);
        audit.record("NOTIFICACION", copy.id, "REENVIAR", n.type + " por " + n.channel + " a " + n.destination);
        events.publishEvent(new Queued());
        return copy;
    }
}
