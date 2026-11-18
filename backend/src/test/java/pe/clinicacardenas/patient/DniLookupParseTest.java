package pe.clinicacardenas.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** La respuesta de los proveedores de consulta RENIEC se lee igual aunque los campos se llamen distinto. */
class DniLookupParseTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void decolectaSoloNombres() throws Exception {
        Map<String, Object> m = DniLookupService.parse("46027897", json.readTree(
                "{\"first_name\":\"ROXANA KARINA\",\"first_last_name\":\"DELGADO\",\"second_last_name\":\"HUAMAN\",\"full_name\":\"DELGADO HUAMAN ROXANA KARINA\",\"document_number\":\"46027897\"}"));
        assertEquals("Roxana Karina", m.get("firstName"));
        assertEquals("Delgado Huaman", m.get("lastName"));
        assertFalse(m.containsKey("birthDate"));
    }

    @Test
    void proveedorConFechaSexoYDireccion() throws Exception {
        Map<String, Object> m = DniLookupService.parse("12345678", json.readTree(
                "{\"nombres\":\"JUAN\",\"apellido_paterno\":\"PEREZ\",\"apellido_materno\":\"QUISPE\",\"fecha_nacimiento\":\"05/04/1992\",\"sexo\":\"MASCULINO\",\"direccion\":\"AV. LIMA 123\",\"ubigeo_reniec\":\"150107\",\"distrito\":\"CHACLACAYO\"}"));
        assertEquals("Juan", m.get("firstName"));
        assertEquals("Perez Quispe", m.get("lastName"));
        assertEquals("1992-04-05", m.get("birthDate"));
        assertEquals("M", m.get("sex"));
        assertEquals("Av. Lima 123", m.get("address"));
        assertEquals("150107", m.get("ubigeo"));
    }

    @Test
    void sinNombresNoHayResultado() throws Exception {
        assertNull(DniLookupService.parse("12345678", json.readTree("{\"message\":\"not found\"}")));
        assertEquals("2000-01-31", DniLookupService.isoDate("2000-01-31T00:00:00"));
        assertNull(DniLookupService.isoDate("31 de enero"));
    }
}
