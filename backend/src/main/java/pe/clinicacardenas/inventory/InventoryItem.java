package pe.clinicacardenas.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String unit;
    public BigDecimal stock = BigDecimal.ZERO;
    @Column(name = "min_stock")
    public BigDecimal minStock = BigDecimal.ZERO;
    public boolean active = true;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public boolean isLow() {
        return active && stock.compareTo(minStock) <= 0;
    }
}
