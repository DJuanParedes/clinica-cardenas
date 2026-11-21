package pe.clinicacardenas.commission;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "commission_payouts")
public class CommissionPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "settlement_id")
    public Long settlementId;
    @Column(name = "paid_on")
    public LocalDate paidOn;
    public BigDecimal amount;
    public String method;
    public String reference;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
