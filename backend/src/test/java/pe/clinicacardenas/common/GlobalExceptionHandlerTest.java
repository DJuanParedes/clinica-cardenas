package pe.clinicacardenas.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Un texto más largo que la columna debe responder 400 con el campo marcado, no 409 ni 500. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void textoDemasiadoLargoEsErrorDeValidacionDelCampo() {
        var ex = new DataIntegrityViolationException("x",
                new SQLException("Data truncation: Data too long for column 'emergency_contact_name' at row 1"));
        var res = handler.handleIntegrity(ex, new MockHttpServletRequest("POST", "/api/patients"));
        assertEquals(400, res.getStatusCode().value());
        assertEquals("VALIDACION", res.getBody().code());
        assertEquals("El texto es demasiado largo para este campo.", res.getBody().fields().get("emergencyContactName"));
    }

    @Test
    void duplicadoSigueSiendoConflicto() {
        var ex = new DataIntegrityViolationException("x", new SQLException("Duplicate entry '45872196' for key 'uk_doc'"));
        var res = handler.handleIntegrity(ex, new MockHttpServletRequest("POST", "/api/patients"));
        assertEquals(409, res.getStatusCode().value());
    }
}
