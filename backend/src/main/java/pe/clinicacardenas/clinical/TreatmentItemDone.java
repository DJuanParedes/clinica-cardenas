package pe.clinicacardenas.clinical;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un procedimiento del plan pasó a REALIZADO (el inventario descuenta sus materiales).
 * v2.10: {@code used} trae lo que el odontólogo indicó que usó en esa atención; si es null se descuenta el kit
 * estándar del procedimiento (Inventario → Consumo por procedimiento).
 */
public record TreatmentItemDone(Long treatmentItemId, Long procedureId, String description, List<Used> used) {
    public record Used(Long itemId, BigDecimal quantity) {}

    public TreatmentItemDone(Long treatmentItemId, Long procedureId, String description) {
        this(treatmentItemId, procedureId, description, null);
    }
}
