package pe.clinica.cardenas.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;

/**
 * Revocación centralizada de JWT para el tráfico que entra desde Internet.
 * En producción los microservicios no publican puertos al host, por lo que
 * toda llamada externa autenticada atraviesa este filtro.
 */
@Component
public class ActiveAccountValidationFilter implements GlobalFilter, Ordered {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/confirm"
    );

    private final WebClient authClient;
    private final Duration authValidationTimeout;

    public ActiveAccountValidationFilter(
            @Value("${services.auth:http://localhost:8081}") String authServiceUrl,
            @Value("${AUTH_VALIDATION_TIMEOUT_SECONDS:3}") long authValidationTimeoutSeconds) {
        // No dependemos de un bean WebClient.Builder autoconfigurado.
        this.authClient = WebClient.builder().baseUrl(authServiceUrl).build();
        this.authValidationTimeout = Duration.ofSeconds(Math.max(1, authValidationTimeoutSeconds));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS
                || path.startsWith("/api/public/")
                || PUBLIC_AUTH_PATHS.contains(path)
                || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // Los endpoints sin JWT siguen llegando al microservicio para que
            // Spring Security aplique la autorización final.
            return chain.filter(exchange);
        }

        // IMPORTANTE: el timeout pertenece EXCLUSIVAMENTE a la validación interna
        // contra auth-service. chain.filter(exchange) queda fuera de ese timeout;
        // una operación clínica válida nunca se cancela solo por tardar >N segundos.
        return validateActiveSession(authorization)
                .flatMap(validationStatus -> {
                    if (validationStatus == HttpStatus.NO_CONTENT) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(validationStatus);
                    return exchange.getResponse().setComplete();
                });
    }

    private Mono<HttpStatus> validateActiveSession(String authorization) {
        return authClient.get()
                .uri("/api/auth/session/validate")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.just(HttpStatus.NO_CONTENT);
                    }
                    if (response.statusCode().value() == 401 || response.statusCode().value() == 403) {
                        return Mono.just(HttpStatus.UNAUTHORIZED);
                    }
                    return Mono.just(HttpStatus.SERVICE_UNAVAILABLE);
                })
                // El reloj termina aquí: solo cubre la llamada interna de revocación.
                .timeout(authValidationTimeout)
                // Un único reintento cubre cortes TCP/timeout transitorios; los HTTP
                // 401/403/5xx se convierten en estado y no se reintentan.
                .retryWhen(Retry.max(1))
                .onErrorReturn(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public int getOrder() { return -100; }
}
