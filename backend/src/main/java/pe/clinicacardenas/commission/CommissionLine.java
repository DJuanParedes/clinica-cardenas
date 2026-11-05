package pe.clinicacardenas.commission;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "commission_lines")
public class CommissionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "settlement_id")
    public Long settlementId;
    @Column(name = "treatment_item_id")
    public Long treatmentItemId;
    @Column(name = "rule_id")
    public Long ruleId;
    @Column(name = "done_at")
    public LocalDateTime doneAt;
    @Column(name = "patient_name")
    public String patientName;
    @Column(name = "procedure_name")
    public String procedureName;
    @Column(name = "rule_label")
    public String ruleLabel;
    @Column(name = "base_amount")
    public BigDecimal baseAmount;
    @Column(name = "commission_amount")
    public BigDecimal commissionAmount;
}
