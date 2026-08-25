package pe.clinica.cardenas.encounterservice.domain;

import pe.clinica.cardenas.security.TenantOwnedEntity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name="TREATMENT_PROFORMAS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TreatmentProforma extends TenantOwnedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=30)
    private String code;

    @Column(nullable=false)
    private Long patientId;

    @Column(nullable=false, length=120)
    private String patientName;

    private Long appointmentId;
    private Long encounterId;

    @Column(length=120)
    private String doctorName;

    @Column(nullable=false, length=30)
    private String status;

    @Column(nullable=false, precision=14, scale=2)
    private BigDecimal discountAmount;

    @Column(nullable=false, precision=14, scale=2)
    private BigDecimal adjustmentAmount;

    @Column(length=500)
    private String adjustmentReason;

    @Column(length=160)
    private String authorizedBy;

    @Column(length=1000)
    private String notes;

    @Column(nullable=false, length=160)
    private String createdBy;

    @Column(nullable=false)
    private LocalDateTime createdAt;

    @Column(nullable=false)
    private LocalDateTime updatedAt;

    private LocalDateTime acceptedAt;

    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="TREATMENT_PROFORMA_LINES", joinColumns=@JoinColumn(name="PROFORMA_ID"))
    @OrderColumn(name="LINE_ORDER")
    @Builder.Default
    private List<ProformaLine> lines = new ArrayList<>();

    @Transient
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Estado UX calculado: evita ofrecer edición cuando ya existen pagos o sesiones vinculadas. */
    @Transient
    @Builder.Default
    private Boolean traceabilityLocked = false;

    @Transient
    private String traceabilityLockReason;

    @Transient
    @Builder.Default
    private Boolean cancellationAllowed = true;

    @Transient
    private String cancellationBlockReason;

    public BigDecimal getSubtotalAmount() {
        return lines == null ? BigDecimal.ZERO : lines.stream().filter(Objects::nonNull)
                .map(ProformaLine::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalAmount() {
        BigDecimal subtotal = getSubtotalAmount();
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        BigDecimal adjustment = adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount;
        return subtotal.subtract(discount).add(adjustment).max(BigDecimal.ZERO);
    }

    public BigDecimal getBalanceAmount() {
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        return getTotalAmount().subtract(paid).max(BigDecimal.ZERO);
    }

    /**
     * Distribuye descuento/ajuste global entre las líneas para que producción, sesiones y cobranza
     * trabajen sobre el importe realmente aceptado y no sobre subtotales brutos incompatibles.
     * La última línea absorbe el residuo de redondeo para que la suma sea exactamente el total del plan.
     */
    public BigDecimal getAcceptedAmountForLine(String lineReference) {
        List<ProformaLine> valid = lines == null ? List.of() : lines.stream().filter(Objects::nonNull).toList();
        if (valid.isEmpty() || lineReference == null) return BigDecimal.ZERO;
        BigDecimal total = getTotalAmount();
        BigDecimal subtotal = getSubtotalAmount();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < valid.size(); i++) {
            ProformaLine line = valid.get(i);
            BigDecimal amount;
            if (i == valid.size() - 1) {
                amount = total.subtract(assigned).max(BigDecimal.ZERO);
            } else if (subtotal.signum() > 0) {
                amount = total.multiply(line.getSubtotal()).divide(subtotal, 2, java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            } else {
                amount = total.divide(BigDecimal.valueOf(valid.size()), 2, java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            }
            if (Objects.equals(lineReference, line.getLineReference())) return amount;
            assigned = assigned.add(amount);
        }
        return BigDecimal.ZERO;
    }
}
