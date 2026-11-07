package pe.clinicacardenas.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.PatientRepository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Entrega la bandeja de salida al microservicio de notificaciones (WhatsApp y correo).
 * Se ejecuta al confirmar cada transacción que encola mensajes y, además, cada 20 s para reintentar
 * los que fallaron por falta de conexión (hasta 5 intentos, con espera creciente).
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final NotificationRepository repo;
    private final PatientRepository patients;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final String baseUrl;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean again = new AtomicBoolean(false);

    public NotificationDispatcher(NotificationRepository repo, PatientRepository patients, TransactionTemplate tx, ObjectMapper json,
                                  @Value("${app.notifications.url}") String baseUrl,
                                  @Value("${app.notifications.enabled:true}") boolean enabled) {
        this.repo = repo;
        this.patients = patients;
        this.tx = tx;
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.enabled = enabled;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(NotificationService.Queued e) {
        flush();
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 20_000)
    public void retryLoop() {
        flush();
    }

    public void flush() {
        if (!enabled) return;
        if (!lock.tryLock()) {
            again.set(true); // ya hay un envío en curso: que repase la bandeja al terminar
            return;
        }
        try {
            do {
                again.set(false);
                List<Long> due = tx.execute(s -> repo.findDue(Dates.now(), PageRequest.of(0, 50)).stream().map(n -> n.id).toList());
                if (due == null) return;
                for (Long id : due) deliver(id);
            } while (again.get());
        } finally {
            lock.unlock();
        }
    }

    private void deliver(Long id) {
        Notification n = tx.execute(s -> repo.findById(id).orElse(null));
        if (n == null || !"PENDIENTE".equals(n.status)) return;
        String status;
        String error = null;
        String providerId = null;
        LocalDateTime sentAt = null;
        boolean retry = false;
        try {
            HttpResponse<String> res = call(n);
            int code = res.statusCode();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = res.body() == null || res.body().isBlank() ? Map.of() : json.readValue(res.body(), Map.class);
            if (code >= 200 && code < 300) {
                providerId = body.get("id") == null ? null : String.valueOf(body.get("id"));
                String state = body.get("estado") == null ? "" : String.valueOf(body.get("estado"));
                if ("ANULAR".equals(n.operation)) {
                    status = "ANULADO";
                } else if (Boolean.TRUE.equals(body.get("duplicado"))) {
                    status = "DUPLICADO";
                    error = "El microservicio ya había procesado este mensaje: no se reenvió.";
                } else if ("PROGRAMAR".equals(n.operation)) {
                    status = "PROGRAMADO";
                } else if ("FALLIDO".equals(state)) {
                    status = "FALLIDO";
                    error = body.get("error") == null ? "El proveedor rechazó el mensaje." : String.valueOf(body.get("error"));
                } else {
                    status = "ENVIADO";
                    sentAt = Dates.now();
                }
            } else if (code == 404 && "ANULAR".equals(n.operation)) {
                status = "ANULADO"; // ya no existía en el microservicio
            } else if (code >= 400 && code < 500) {
                status = "FALLIDO";
                error = body.get("error") == null ? "Solicitud rechazada (HTTP " + code + ")" : String.valueOf(body.get("error"));
            } else {
                status = "PENDIENTE";
                retry = true;
                error = "Microservicio no disponible (HTTP " + code + ")";
            }
        } catch (Exception e) {
            status = "PENDIENTE";
            retry = true;
            error = "Sin conexión con el microservicio de notificaciones";
        }
        final String fStatus = status, fError = error, fProvider = providerId;
        final LocalDateTime fSent = sentAt;
        final boolean fRetry = retry;
        tx.executeWithoutResult(s -> repo.findById(id).ifPresent(x -> {
            if (!"PENDIENTE".equals(x.status)) return; // anulada mientras se enviaba
            x.attempts++;
            x.updatedAt = Dates.now();
            x.lastError = fError;
            if (fProvider != null) x.providerId = fProvider;
            if (fRetry) {
                if (x.attempts >= MAX_ATTEMPTS) {
                    x.status = "FALLIDO";
                } else {
                    x.nextAttemptAt = Dates.now().plusSeconds(30L * (1L << (x.attempts - 1)));
                }
            } else {
                x.status = fStatus;
                x.nextAttemptAt = null;
                if (fSent != null) x.sentAt = fSent;
            }
            repo.save(x);
        }));
        if (retry) log.debug("Notificación {} pendiente: {}", id, error);
    }

    private HttpResponse<String> call(Notification n) throws Exception {
        HttpRequest.Builder b;
        if ("ANULAR".equals(n.operation)) {
            String target = n.idempotencyKey.substring("anular:".length());
            b = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/recordatorios/" + URLEncoder.encode(target, StandardCharsets.UTF_8))).DELETE();
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clave", n.idempotencyKey);
            payload.put("canal", n.channel);
            payload.put("destino", n.destination);
            payload.put("asunto", n.subject);
            payload.put("cuerpo", n.body);
            payload.put("tipo", n.type);
            payload.put("referencia", n.appointmentId == null ? null : "cita-" + n.appointmentId);
            if (n.patientId != null) {
                // nombre del paciente y si es real o de ejemplo: el microservicio decide si lo envía de verdad
                patients.findById(n.patientId).ifPresent(p -> {
                    Map<String, Object> c = ContactSync.contact(p);
                    Map<String, Object> pac = new LinkedHashMap<>();
                    pac.put("id", p.id);
                    pac.put("nombre", c.get("nombre"));
                    pac.put("demo", c.get("demo"));
                    payload.put("paciente", pac);
                });
            }
            String path = "/v1/mensajes";
            if ("PROGRAMAR".equals(n.operation)) {
                path = "/v1/recordatorios";
                payload.put("enviarEn", n.scheduledFor == null ? null : n.scheduledFor.atZone(Dates.ZONE).toOffsetDateTime().toString());
            }
            b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8));
        }
        return http.send(b.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Resultado que informa el microservicio cuando envía un recordatorio programado (webhook). */
    public boolean applyCallback(String key, String state, String error, String providerId) {
        Boolean ok = tx.execute(s -> repo.findByIdempotencyKey(key).map(n -> {
            if ("ANULADO".equals(n.status)) return true;
            // Estados de entrega de WhatsApp (webhook de Meta): ENTREGADO y LEIDO solo avanzan, nunca retroceden
            if ("ENTREGADO".equals(state) || "LEIDO".equals(state)) {
                if ("LEIDO".equals(n.status) || ("ENTREGADO".equals(n.status) && "ENTREGADO".equals(state))) return true;
                n.status = state;
                n.updatedAt = Dates.now();
                repo.save(n);
                return true;
            }
            n.status = "ENVIADO".equals(state) ? "ENVIADO" : "FALLIDO";
            n.lastError = error;
            if (providerId != null) n.providerId = providerId;
            if ("ENVIADO".equals(n.status)) n.sentAt = Dates.now();
            n.updatedAt = Dates.now();
            repo.save(n);
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(ok);
    }
}
