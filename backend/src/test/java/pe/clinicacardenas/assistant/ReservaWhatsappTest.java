package pe.clinicacardenas.assistant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Reserva por WhatsApp: el paciente escribe la fecha como le sale natural. */
class ReservaWhatsappTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 18); // miércoles

    private static LocalDate p(String text) {
        return BookingFlow.parseDate(TextSearch.normalize(text), HOY);
    }

    @Test
    void entiendeFechasRelativas() {
        assertEquals(HOY, p("hoy"));
        assertEquals(HOY.plusDays(1), p("mañana por favor"));
        assertEquals(HOY.plusDays(2), p("pasado mañana"));
    }

    @Test
    void entiendeDiasDeLaSemanaComoElProximo() {
        assertEquals(LocalDate.of(2026, 11, 23), p("el lunes"));
        assertEquals(LocalDate.of(2026, 11, 25), p("miércoles"));   // hoy es miércoles: el de la próxima semana
        assertEquals(LocalDate.of(2026, 11, 21), p("sábado en la mañana"));
    }

    @Test
    void entiendeFechasNumericasYConMes() {
        assertEquals(LocalDate.of(2026, 11, 28), p("28/11"));
        assertEquals(LocalDate.of(2026, 12, 3), p("3-12-2026"));
        assertEquals(LocalDate.of(2027, 1, 5), p("5/1"));             // enero ya pasó este año: el próximo
        assertEquals(LocalDate.of(2026, 12, 2), p("2 de diciembre"));
        assertEquals(LocalDate.of(2027, 9, 10), p("10 de setiembre"));
    }

    @Test
    void rechazaLoQueNoEsFecha() {
        assertNull(p("cuando puedan"));
        assertNull(p("31/02"));
    }

    @Test
    void pedirOtraCitaTambienEsReservar() {
        assertEquals("DISPONIBILIDAD", AssistantService.intent("quiero otra cita"));
        assertEquals("DISPONIBILIDAD", AssistantService.intent("necesito una nueva cita"));
    }
}
