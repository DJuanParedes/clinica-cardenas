package pe.clinicacardenas.cash;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** HU-13: cobro con su medio de pago. */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "treatment_item_id")
    public Long treatmentItemId;
    public BigDecimal amount;
    public String method;
    public String reference;
    public String notes;
    @Column(name = "paid_at")
    public LocalDateTime paidAt;
    public String cashier;
    /** VALIDO, ANULADO */
    public String status;
    @Column(name = "void_reason")
    public String voidReason;
    @Column(name = "voided_at")
    public LocalDateTime voidedAt;
    @Column(name = "voided_by")
    public String voidedBy;
}
