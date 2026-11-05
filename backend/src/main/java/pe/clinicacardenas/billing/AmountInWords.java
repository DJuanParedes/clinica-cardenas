package pe.clinicacardenas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Importe en letras para la leyenda 1000 del comprobante: "SON: CIENTO VEINTE CON 00/100 SOLES". */
public final class AmountInWords {

    private static final String[] UNITS = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE",
            "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] TENS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] HUNDREDS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private AmountInWords() {}

    public static String of(BigDecimal amount) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        long integer = a.longValue();
        int cents = a.subtract(BigDecimal.valueOf(integer)).movePointRight(2).intValue();
        String words = integer == 0 ? "CERO" : convert(integer).trim();
        return "SON: " + words + " CON " + String.format("%02d", cents) + "/100 SOLES";
    }

    private static String convert(long n) {
        if (n >= 1_000_000) {
            long m = n / 1_000_000;
            String head = m == 1 ? "UN MILLON" : convert(m).trim() + " MILLONES";
            return head + " " + convert(n % 1_000_000);
        }
        if (n >= 1000) {
            long k = n / 1000;
            String head = k == 1 ? "MIL" : apocope(convert(k).trim()) + " MIL";
            return head + " " + convert(n % 1000);
        }
        if (n >= 100) {
            if (n == 100) return "CIEN";
            return HUNDREDS[(int) (n / 100)] + " " + convert(n % 100);
        }
        if (n >= 30) {
            int t = (int) (n / 10), u = (int) (n % 10);
            return TENS[t] + (u > 0 ? " Y " + UNITS[u] : "");
        }
        return UNITS[(int) n];
    }

    /** "UNO" -> "UN" delante de MIL (veintiún mil, treinta y un mil). */
    private static String apocope(String s) {
        if (s.endsWith("VEINTIUNO")) return s.substring(0, s.length() - 9) + "VEINTIUN";
        if (s.endsWith("UNO")) return s.substring(0, s.length() - 3) + "UN";
        return s;
    }
}
