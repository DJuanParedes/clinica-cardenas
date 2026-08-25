package pe.clinica.cardenas.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, rootMessage(ex));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<Map<String,Object>> notFound(java.util.NoSuchElementException ex) {
        return response(HttpStatus.NOT_FOUND, "Registro no encontrado");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Datos inválidos");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,Object>> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return response(status == null ? HttpStatus.BAD_REQUEST : status,
                ex.getReason() == null ? "Solicitud rechazada" : ex.getReason());
    }


    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String,Object>> forbidden(org.springframework.security.access.AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> general(Exception ex) {
        log.error("Error interno no controlado", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servicio");
    }

    private ResponseEntity<Map<String,Object>> response(HttpStatus status, String message) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private String rootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? ex.getMessage() : t.getMessage();
    }
}
