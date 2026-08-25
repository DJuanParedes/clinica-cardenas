package pe.clinica.cardenas.encounterservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.math.BigDecimal;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProformaLine {
    /** Identificador estable para vincular citas/pagos aunque cambie el orden visual de las líneas. */
    @Column(name="LINE_REFERENCE", length=64)
    private String lineReference;

    @Column(name="TREATMENT_NAME", nullable=false, length=180)
    private String treatmentName;

    @Column(name="TOOTH_ZONE", length=80)
    private String toothZone;

    @Column(name="QUANTITY", nullable=false)
    private Integer quantity;

    /** Número de citas/sesiones clínicas previstas para completar esta línea del plan. */
    @Column(name="PLANNED_SESSIONS")
    private Integer plannedSessions = 1;

    @Column(name="UNIT_PRICE", nullable=false, precision=14, scale=2)
    private BigDecimal unitPrice;

    @Column(name="TREATMENT_STATUS", nullable=false, length=30)
    private String treatmentStatus;

    public BigDecimal getSubtotal() {
        int qty = quantity == null ? 0 : Math.max(0, quantity);
        return (unitPrice == null ? BigDecimal.ZERO : unitPrice).multiply(BigDecimal.valueOf(qty));
    }
}
