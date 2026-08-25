package pe.clinica.cardenas.inventoryservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.inventoryservice.domain.InventoryItem;
import java.util.List;
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findAllByOrderByNameAsc();
}
