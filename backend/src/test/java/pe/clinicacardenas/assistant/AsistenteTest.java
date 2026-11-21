package pe.clinicacardenas.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Asistente de WhatsApp: intención del mensaje y búsqueda en la base de conocimiento (sin base de datos). */
class AsistenteTest {

    @Test
    void entiendeLaIntencionDelPaciente() {
        assertEquals("SALUDO", AssistantService.intent("Buenas tardes"));
        assertEquals("MI_CITA", AssistantService.intent("¿a qué hora tengo cita?"));
        assertEquals("REPROGRAMAR", AssistantService.intent("quiero cambiar mi cita para otro día"));
        assertEquals("CANCELAR", AssistantService.intent("no voy a poder ir, cancelen por favor"));
        assertEquals("CONFIRMAR", AssistantService.intent("sí, confirmo"));
        assertEquals("PRECIO", AssistantService.intent("¿cuánto cuesta una limpieza?"));
        assertEquals("PAGO", AssistantService.intent("¿cuánto debo?"));
        assertEquals("HORARIO", AssistantService.intent("¿atienden los domingos?"));
        assertEquals("UBICACION", AssistantService.intent("¿dónde quedan?"));
        assertEquals("URGENCIA", AssistantService.intent("me duele mucho la muela y está hinchado"));
        assertEquals("HUMANO", AssistantService.intent("quiero hablar con una persona"));
        assertEquals("DISPONIBILIDAD", AssistantService.intent("¿hay cupo mañana?"));
        assertEquals("OTRO", AssistantService.intent("¿atienden niños?"));
    }

    @Test
    void buscaEnLaBaseDeConocimientoConPluralesYTildes() {
        List<TextSearch.Doc> docs = List.of(
                new TextSearch.Doc("1", "PRECIO", "Profilaxis (limpieza dental)", "Profilaxis: S/ 80"),
                new TextSearch.Doc("2", "FAQ", "¿Hacen blanqueamiento dental?", "Sí, en consultorio"),
                new TextSearch.Doc("3", "FAQ", "¿Atienden a niños? ¿Desde qué edad?", "Desde los 3 años"));
        assertEquals("1", TextSearch.search(docs, "cuánto cuesta una limpieza", 1).get(0).doc().id());
        assertEquals("2", TextSearch.search(docs, "¿hacen blanqueamientos?", 1).get(0).doc().id());
        assertEquals("3", TextSearch.search(docs, "atienden a mi hijo niño de 5 años", 1).get(0).doc().id());
        assertTrue(TextSearch.search(docs, "hola", 3).isEmpty());
    }
}
