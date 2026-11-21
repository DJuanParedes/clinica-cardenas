package pe.clinicacardenas.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verificación en 2 pasos: vectores oficiales del RFC 6238 (SHA-1, 6 dígitos). */
class TotpTest {

    /** "12345678901234567890" en base32. */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void generaLosCodigosDelRfc() {
        assertEquals("287082", Totp.generate(SECRETO_RFC, 59 / 30));
        assertEquals("081804", Totp.generate(SECRETO_RFC, 1111111109L / 30));
        assertEquals("050471", Totp.generate(SECRETO_RFC, 1111111111L / 30));
    }

    @Test
    void aceptaElCodigoActualYUnPasoDeDiferencia() {
        long t = 1111111109L;
        assertTrue(Totp.verify(SECRETO_RFC, "081804", t));
        assertTrue(Totp.verify(SECRETO_RFC, "081804", t + 30));
        assertFalse(Totp.verify(SECRETO_RFC, "081804", t + 120));
        assertFalse(Totp.verify(SECRETO_RFC, "12345", t));
        assertFalse(Totp.verify(null, "081804", t));
    }

    @Test
    void elSecretoNuevoEsBase32YFuncionaIdaYVuelta() {
        String s = Totp.newSecret();
        assertEquals(32, s.length());
        assertTrue(s.matches("[A-Z2-7]+"));
        String code = Totp.generate(s, 1000);
        assertTrue(Totp.verify(s, code, 1000L * 30));
    }
}
