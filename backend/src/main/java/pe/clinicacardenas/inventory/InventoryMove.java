package pe.clinicacardenas.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_moves")
public class InventoryMove {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "item_id")
    public Long itemId;
    public String kind;
    public BigDecimal quantity;
    @Column(name = "stock_after")
    public BigDecimal stockAfter;
    public String reason;
    @Column(name = "treatment_item_id")
    public Long treatmentItemId;
    @Column(name = "lot_id")
    public Long lotId;
    @Column(name = "purchase_id")
    public Long purchaseId;
    @Column(name = "request_id")
    public Long requestId;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    /** v2.8: kardex valorizado. */
    @Column(name = "unit_cost")
    public BigDecimal unitCost;
    @Column(name = "total_cost")
    public BigDecimal totalCost;
    @Column(name = "value_after")
    public BigDecimal valueAfter;
    @Column(name = "patient_id")
    public Long patientId;
    @Column(name = "procedure_id")
    public Long procedureId;
    @Column(name = "created_by")
    public String createdBy;
}
