package pe.clinicacardenas.clinical;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Promociones: el precio final nunca es negativo y se redondea a céntimos. */
class PromocionesTest {

    private static BigDecimal b(String v) { return new BigDecimal(v); }

    @Test
    void porcentajeMontoYPrecioPromocional() {
        assertEquals(b("96.00"), PromotionService.apply(b("120.00"), "PORCENTAJE", b("20")));
        assertEquals(b("100.00"), PromotionService.apply(b("120.00"), "MONTO", b("20")));
        assertEquals(b("89.00"), PromotionService.apply(b("120.00"), "PRECIO", b("89")));
    }

    @Test
    void nuncaDejaPrecioNegativo() {
        assertEquals(b("0.00"), PromotionService.apply(b("50.00"), "MONTO", b("80")));
    }

    @Test
    void redondeaACentimos() {
        assertEquals(b("66.67"), PromotionService.apply(b("100.00"), "PORCENTAJE", b("33.33")));
    }
}
