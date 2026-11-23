package pe.clinicacardenas.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Modelo de lenguaje que redacta las respuestas del asistente (la "G" de RAG). Proveedores:
 *   - claude: API de Anthropic (ANTHROPIC_API_KEY; modelo ASISTENTE_MODELO, por defecto claude-haiku-5-5).
 *   - openai: cualquier API compatible con OpenAI Chat Completions: OpenAI, Groq, o un modelo local con Ollama /
 *     LM Studio (ASISTENTE_URL, p. ej. http://host.docker.internal:11434/v1; ASISTENTE_API_KEY opcional).
 *   - local: sin modelo externo; el asistente responde con su modelo propio de intenciones + búsqueda (siempre disponible).
 * Si el proveedor falla o tarda, se usa la respuesta local: el paciente nunca se queda sin respuesta.
 */
@Component
public class LanguageModel {
    private static final String CLAUDE = "claude";
    private static final String CONTENT = "content";
    private static final String LOCAL = "local";
    private static final String OPENAI = "openai";


    private static final Logger log = LoggerFactory.getLogger(LanguageModel.class);

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String provider;
    private final String model;
    private final String apiKey;
    private final String url;
    private final int timeoutMs;
    private volatile String lastError;

    public LanguageModel(ObjectMapper json,
                         @Value("${app.assistant.provider:}") String provider,
                         @Value("${app.assistant.model:}") String model,
                         @Value("${app.assistant.api-key:}") String apiKey,
                         @Value("${app.assistant.anthropic-key:}") String anthropicKey,
                         @Value("${app.assistant.url:}") String url,
                         @Value("${app.assistant.timeout-ms:6000}") int timeoutMs) {
        this.json = json;
        String p = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() && !anthropicKey.isBlank()) p = CLAUDE;
        else if (p.isEmpty()) p = url.isBlank() ? LOCAL : OPENAI;
        this.provider = p;
        this.apiKey = CLAUDE.equals(p) && !anthropicKey.isBlank() ? anthropicKey : apiKey;
        this.model = model.isBlank() ? defaultModel(p) : model.trim();
        String claudeUrl = CLAUDE.equals(p) ? "https://api.anthropic.com/v1/messages" : "";
        this.url = url.isBlank() ? claudeUrl : url.trim().replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
    }

    public boolean enabled() {
        return (CLAUDE.equals(provider) && !apiKey.isBlank()) || (OPENAI.equals(provider) && !url.isBlank());
    }

    public String engineName() { return enabled() ? provider + ":" + model : LOCAL; }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proveedor", enabled() ? provider : LOCAL);
        m.put("modelo", enabled() ? model : "Modelo local de intenciones + búsqueda BM25");
        m.put("externo", enabled());
        m.put("ultimoError", lastError);
        return m;
    }

    /** Redacta con el modelo externo. Devuelve null si no hay modelo o si falló (se usa la respuesta local). */
    public String complete(String system, List<Map<String, String>> messages) {
        if (!enabled()) return null;
        try {
            String text = CLAUDE.equals(provider) ? claude(system, messages) : openai(system, messages);
            lastError = null;
            return text == null || text.isBlank() ? null : text.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "Interrumpido";
            return null;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Asistente: el modelo {} no respondió ({}); se usa la respuesta local", engineName(), lastError);
            return null;
        }
    }

    private String claude(String system, List<Map<String, String>> messages) throws java.io.IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 450);
        body.put("system", system);
        body.put("messages", messages);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode() + " " + res.body().substring(0, Math.min(200, res.body().length())));
        JsonNode content = json.readTree(res.body()).path(CONTENT);
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : content) if ("text".equals(c.path("type").asText())) sb.append(c.path("text").asText());
        return sb.toString();
    }

    private String openai(String system, List<Map<String, String>> messages) throws java.io.IOException, InterruptedException {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", CONTENT, system));
        all.addAll(messages);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 450);
        body.put("temperature", 0.3);
        body.put("messages", all);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (!apiKey.isBlank()) b.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode() + " " + res.body().substring(0, Math.min(200, res.body().length())));
        return json.readTree(res.body()).path("choices").path(0).path("message").path(CONTENT).asText();
    }

    private static String defaultModel(String provider) {
        if (CLAUDE.equals(provider)) return "claude-haiku-5-5";
        return OPENAI.equals(provider) ? "llama3.2" : LOCAL;
    }
}
