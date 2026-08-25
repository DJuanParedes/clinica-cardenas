package pe.clinica.cardenas.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class SchedulingProvisioningClient {
    private final RestClient client;

    public SchedulingProvisioningClient(@Value("${services.appointments:http://localhost:8083}") String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public Map<String,Object> findDoctorByEmail(String email, String authorization) {
        return doctors(authorization).stream()
                .filter(d -> email != null && email.equalsIgnoreCase(text(d.get("accountEmail"))))
                .findFirst().orElse(null);
    }

    public void ensureDoctor(String previousEmail, String fullName, String email, String specialty, String branch,
                             String shiftStart, String shiftEnd, boolean active, String authorization) {
        Map<String,Object> body = Map.of(
                "fullName", fullName,
                "accountEmail", email,
                "specialty", specialty,
                "branch", branch,
                "shiftStart", shiftStart,
                "shiftEnd", shiftEnd,
                "active", active
        );
        Map<String,Object> existing = findDoctorByEmail(previousEmail == null || previousEmail.isBlank() ? email : previousEmail, authorization);
        if (existing == null && previousEmail != null && !previousEmail.equalsIgnoreCase(email)) existing = findDoctorByEmail(email, authorization);
        if (existing == null) {
            client.post().uri("/api/scheduling/doctors").header(HttpHeaders.AUTHORIZATION, authorization).body(body).retrieve().toBodilessEntity();
        } else {
            client.put().uri("/api/scheduling/doctors/{id}", existing.get("id")).header(HttpHeaders.AUTHORIZATION, authorization).body(body).retrieve().toBodilessEntity();
        }
    }

    public void setDoctorActive(String email, boolean active, String authorization) {
        Map<String,Object> existing = findDoctorByEmail(email, authorization);
        if (existing == null) return;
        client.method(HttpMethod.PATCH).uri(uri -> uri.path("/api/scheduling/doctors/{id}/active").queryParam("value", active).build(existing.get("id")))
                .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().toBodilessEntity();
    }

    private List<Map<String,Object>> doctors(String authorization) {
        List<Map<String,Object>> out = client.get().uri("/api/scheduling/doctors")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve().body(new ParameterizedTypeReference<List<Map<String,Object>>>(){});
        return out == null ? List.of() : out;
    }
    private static String text(Object v) { return v == null ? "" : String.valueOf(v); }
}
