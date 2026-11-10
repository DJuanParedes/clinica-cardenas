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
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "created_by")
    public String createdBy;
}
