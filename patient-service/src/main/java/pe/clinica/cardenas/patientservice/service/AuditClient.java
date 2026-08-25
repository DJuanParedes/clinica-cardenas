package pe.clinica.cardenas.patientservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditClient {
    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);
    private final RestClient client;

    public AuditClient(@Value("${services.encounters:http://localhost:8092}") String url) {
        this.client = RestClient.create(url);
    }

    public void log(String entityId, String action, Object before, Object after, String reason, String authorization) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("module", "PACIENTES");
            body.put("entityType", "Patient");
            body.put("entityId", entityId);
            body.put("action", action);
            body.put("before", before);
            body.put("after", after);
            body.put("reason", reason);
            client.post().uri("/api/audit/events")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(body).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Auditoría de pacientes no disponible: {}", ex.getMessage());
        }
    }
}
