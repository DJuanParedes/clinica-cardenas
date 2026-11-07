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

    public NotificationService(NotificationRepository repo, ApplicationEventPublisher events, AuditService audit,
                               @Value("${app.company.trade-name}") String clinicName,
                               @Value("${app.company.address}") String clinicAddress,
                               @Value("${app.notifications.clinic-phone:987 654 000}") String clinicPhone) {
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
        n.scheduledFor = scheduledFor;
        n.status = "PENDIENTE";
        n.createdAt = Dates.now();
        repo.save(n);
        events.publishEvent(new Queued());
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
        LocalDateTime at = reminderTime(a.startAt, Dates.now());
        if (at == null) return;
        for (Target t : targets(a.patient)) {
            add(a.patient, a.id, "RECORDATORIO", "PROGRAMAR", t, "Recordatorio de tu cita · " + clinicName,
                    body(t.channel(), "Hola " + firstName(a.patient) + ", te recordamos tu cita en " + clinicName + ":", a,
                            replyHint(t.channel())),
                    reminderKey(a, t.channel()), at);
        }
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
        scheduleReminder(a);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentRescheduled(Appointment a, LocalDateTime previousStart, String previousDentist) {
        unscheduleReminders(a);
        for (Target t : targets(a.patient)) {
            add(a.patient, a.id, "CAMBIO_CITA", "ENVIAR", t, "Tu cita fue reprogramada · " + clinicName,
                    body(t.channel(), "Hola " + firstName(a.patient) + ", tu cita del " + when(previousStart)
                            + (previousDentist.equals(a.dentist.getFullName()) ? "" : " con " + previousDentist)
                            + " cambió. Este es el nuevo horario:", a, "Si el nuevo horario no te acomoda, comunícate con nosotros."),
                    "cambio:" + a.id + ":" + a.startAt.format(KEY) + ":" + t.channel(), null);
        }
        scheduleReminder(a);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentCancelled(Appointment a, String reason) {
        unscheduleReminders(a);
        for (Target t : targets(a.patient)) {
            add(a.patient, a.id, "CANCELACION", "ENVIAR", t, "Tu cita fue cancelada · " + clinicName,
                    body(t.channel(), "Hola " + firstName(a.patient) + ", tu cita fue cancelada"
                            + (reason == null || reason.isBlank() ? "." : " (" + reason.trim() + ")."), a,
                            "Para programar una nueva cita escríbenos o llámanos al " + clinicPhone + "."),
                    "cancelacion:" + a.id + ":" + t.channel(), null);
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
            add(a.patient, a.id, "RECORDATORIO", "ENVIAR", t, "Recordatorio de tu cita · " + clinicName,
                    body(t.channel(), "Hola " + firstName(a.patient) + ", te recordamos tu cita en " + clinicName + ":", a,
                            replyHint(t.channel())), key, null);
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
