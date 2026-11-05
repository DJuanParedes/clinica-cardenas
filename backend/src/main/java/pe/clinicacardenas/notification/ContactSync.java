package pe.clinicacardenas.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agenda de WhatsApp: cuando la clínica registra o edita un paciente, su número y su nombre se guardan
 * automáticamente en el microservicio de notificaciones. Así los mensajes salen a nombre del paciente y el
 * microservicio sabe que es un paciente real (los de ejemplo, creados al instalar, tienen demo = true).
 */
@Component
public class ContactSync {

    /** Lo publica PatientService después de guardar un paciente. */
    public record PatientSaved(Long patientId) {}

    private static final Logger log = LoggerFactory.getLogger(ContactSync.class);

    private final PatientRepository patients;
    private final ObjectMapper json;
    private final String baseUrl;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ContactSync(PatientRepository patients, ObjectMapper json,
                       @Value("${app.notifications.url}") String baseUrl,
                       @Value("${app.notifications.enabled:true}") boolean enabled) {
        this.patients = patients;
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.enabled = enabled;
    }

    /** Datos que el microservicio necesita de un paciente. */
    public static Map<String, Object> contact(Patient p) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("pacienteId", p.id);
        c.put("nombre", p.getFullName());
        c.put("telefono", p.phone);
        c.put("correo", p.email);
        c.put("demo", isDemo(p));
        return c;
    }

    /** Pacientes de ejemplo creados al instalar (columna demo). */
    public static boolean isDemo(Patient p) {
        return p.demo;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPatientSaved(PatientSaved e) {
        patients.findById(e.patientId()).ifPresent(p -> post(List.of(contact(p))));
    }

    /** Al arrancar y cada 30 minutos se envía la agenda completa (por si el microservicio se reinició sin datos). */
    @Scheduled(initialDelay = 20_000, fixedDelay = 30 * 60_000)
    public void syncAll() {
        post(patients.findAll().stream().map(ContactSync::contact).toList());
    }

    private void post(List<Map<String, Object>> contacts) {
        if (!enabled || contacts.isEmpty()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/contactos"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(contacts), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 300) log.warn("Agenda de WhatsApp: el microservicio respondió {}", res.statusCode());
        } catch (Exception ex) {
            log.debug("Agenda de WhatsApp no sincronizada: {}", ex.getMessage());
        }
    }
}
