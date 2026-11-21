package pe.clinicacardenas.commission;

import jakarta.persistence.*;
import pe.clinicacardenas.agenda.Dentist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** HU-18: liquidación de comisiones de un odontólogo por período. */
@Entity
@Table(name = "commission_settlements")
public class CommissionSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dentist_id")
    public Dentist dentist;
    @Column(name = "period_from")
    public LocalDate periodFrom;
    @Column(name = "period_to")
    public LocalDate periodTo;
    @Column(name = "total_amount")
    public BigDecimal totalAmount;
    @Column(name = "paid_amount")
    public BigDecimal paidAmount = BigDecimal.ZERO;
    /** PENDIENTE, PARCIAL, PAGADA, ANULADA */
    public String status;
    public String notes;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;

    public BigDecimal getPendingAmount() {
        return "ANULADA".equals(status) ? BigDecimal.ZERO : totalAmount.subtract(paidAmount);
    }
}
