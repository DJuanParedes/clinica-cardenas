package pe.clinicacardenas.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import pe.clinicacardenas.files.ImageService;

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
    /** INSUMO (se consume), INSTRUMENTAL (se reutiliza/esteriliza) o MEDICAMENTO. */
    public String category = "INSUMO";
    public String brand;
    /** Laboratorio o proveedor habitual. */
    public String supplier;
    @Column(name = "unit_cost")
    public BigDecimal unitCost;
    @Column(name = "tracks_expiry")
    public boolean tracksExpiry = true;
    public BigDecimal stock = BigDecimal.ZERO;
    @Column(name = "min_stock")
    public BigDecimal minStock = BigDecimal.ZERO;
    public boolean active = true;
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @JsonIgnore
    @Column(name = "image_path")
    public String imagePath;

    public String getImageUrl() {
        return ImageService.url(imagePath);
    }

    public boolean isLow() {
        return active && stock.compareTo(minStock) <= 0;
    }
}
