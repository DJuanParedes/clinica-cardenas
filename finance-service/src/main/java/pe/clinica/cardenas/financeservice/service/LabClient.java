package pe.clinica.cardenas.financeservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class LabClient {
    private final RestClient client;

    public LabClient(@Value("${services.lab:http://localhost:8086}") String url) {
        this.client = RestClient.create(url);
    }

    public List<Map<String,Object>> orders(String auth) {
        List<Map<String,Object>> out = client.get().uri("/api/lab-orders")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .retrieve().body(new ParameterizedTypeReference<List<Map<String,Object>>>(){});
        return out == null ? List.of() : out;
    }
}
