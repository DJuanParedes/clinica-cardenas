package pe.clinicacardenas.clinical;

import java.util.Set;

/** Notación FDI y condiciones admitidas en el odontograma (HU-05). */
public final class OdontogramRules {

    private OdontogramRules() {}

    public static final Set<String> FACES = Set.of("V", "L", "M", "D", "O");
    public static final String WHOLE = "X";

    /** Condiciones que se marcan en una cara de la pieza. */
    public static final Set<String> FACE_CONDITIONS = Set.of("CARIES", "RESTAURACION", "SELLANTE", "FRACTURA", "RESTAURACION_DEFECTUOSA");

    /** Condiciones que afectan a la pieza completa. */
    public static final Set<String> TOOTH_CONDITIONS = Set.of("AUSENTE", "EXTRACCION_INDICADA", "CORONA", "CORONA_INDICADA",
            "ENDODONCIA", "ENDODONCIA_INDICADA", "IMPLANTE", "MOVILIDAD");

    public static boolean isValidTooth(int t) {
        int q = t / 10, n = t % 10;
        if (q >= 1 && q <= 4) return n >= 1 && n <= 8;   // permanentes
        if (q >= 5 && q <= 8) return n >= 1 && n <= 5;   // deciduas
        return false;
    }

    /** Devuelve null si es válida o el motivo del error. */
    public static String check(int tooth, String face, String condition) {
        if (!isValidTooth(tooth)) return "La pieza " + tooth + " no existe en la notación FDI.";
        if (WHOLE.equals(face)) {
            return TOOTH_CONDITIONS.contains(condition) ? null : "La condición " + condition + " no aplica a la pieza completa.";
        }
        if (!FACES.contains(face)) return "Cara dental inválida: " + face + ".";
        return FACE_CONDITIONS.contains(condition) ? null : "La condición " + condition + " no aplica a una cara.";
    }
}
