package pe.clinicacardenas.billing;

/** Validación de RUC peruano (11 dígitos, prefijo válido y dígito verificador módulo 11). */
public final class RucValidator {
    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucValidator() {}

    public static boolean isValid(String ruc) {
        if (ruc == null || !ruc.matches("^\\d{11}$")) return false;
        String prefix = ruc.substring(0, 2);
        if (!(prefix.equals("10") || prefix.equals("15") || prefix.equals("16") || prefix.equals("17") || prefix.equals("20"))) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) sum += (ruc.charAt(i) - '0') * WEIGHTS[i];
        int check = 11 - (sum % 11);
        if (check == 10) check = 0;
        if (check == 11) check = 1;
        return check == ruc.charAt(10) - '0';
    }
}
