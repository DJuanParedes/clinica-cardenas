package pe.clinica.cardenas.dashboardservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RemoteApiClient {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().build();

    public List<Map<String,Object>> list(String url, String authorization) {
        try {
            HttpResponse<String> response = send(url, authorization);
            if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception ex) { return List.of(); }
    }

    public Map<String,Object> map(String url, String authorization) {
        try {
            HttpResponse<String> response = send(url, authorization);
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Map.of();
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception ex) { return Map.of(); }
    }

    public long count(String url, String auth) { return list(url, auth).size(); }

    public long countMatching(String url, String field, Set<String> values, String auth) {
        return list(url, auth).stream().map(item -> Objects.toString(item.get(field), "")).filter(values::contains).count();
    }

    public BigDecimal sum(String url, String field, String auth) {
        return list(url, auth).stream().map(x -> x.get(field)).filter(Objects::nonNull).map(Object::toString)
                .map(value -> { try { return new BigDecimal(value); } catch (Exception ex) { return BigDecimal.ZERO; } })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private HttpResponse<String> send(String url, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (authorization != null && !authorization.isBlank()) builder.header("Authorization", authorization);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
