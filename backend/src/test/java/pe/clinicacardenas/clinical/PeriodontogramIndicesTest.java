package pe.clinicacardenas.clinical;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Índices del periodontograma: solo cuentan las piezas presentes (6 sitios por pieza). */
class PeriodontogramIndicesTest {

    @Test
    void indicesSobrePiezasPresentes() {
        PeriodontogramService.Tooth t11 = new PeriodontogramService.Tooth();
        t11.v.plaque = List.of(true, true, false);
        t11.v.bleed = List.of(true, false, false);
        t11.v.ps = List.of(5, 3, 2);
        PeriodontogramService.Tooth t18 = new PeriodontogramService.Tooth();
        t18.missing = true;
        t18.v.plaque = List.of(true, true, true); // no cuenta: pieza ausente
        var data = PeriodontogramService.normalize(Map.of("11", t11, "18", t18));
        var ix = PeriodontogramService.indices(data);
        assertEquals(31, ix.teeth());
        assertEquals(31 * 6, ix.sites());
        assertEquals(1, ix.deepSites());
        assertEquals(new java.math.BigDecimal("1.08"), ix.plaque());   // 2 de 186 sitios
        assertEquals(new java.math.BigDecimal("0.54"), ix.bleeding()); // 1 de 186
    }

    @Test
    void rechazaValoresFueraDeRango() {
        PeriodontogramService.Tooth t = new PeriodontogramService.Tooth();
        t.v.ps = List.of(20, 0, 0);
        assertThrows(pe.clinicacardenas.common.ApiException.class, () -> PeriodontogramService.normalize(Map.of("21", t)));
        PeriodontogramService.Tooth m = new PeriodontogramService.Tooth();
        m.furcation = 2;
        assertEquals(0, PeriodontogramService.normalize(Map.of("11", m)).get("11").furcation); // incisivo: sin furca
    }
}
