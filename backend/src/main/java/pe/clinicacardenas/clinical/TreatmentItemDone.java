package pe.clinicacardenas.clinical;

/** Un procedimiento del plan pasó a REALIZADO (el inventario descuenta sus materiales). */
public record TreatmentItemDone(Long treatmentItemId, Long procedureId, String description) {}
