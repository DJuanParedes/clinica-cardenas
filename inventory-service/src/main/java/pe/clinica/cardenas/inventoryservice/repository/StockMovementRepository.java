package pe.clinica.cardenas.inventoryservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.inventoryservice.domain.StockMovement;
import java.util.List;
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findTop200ByOrderByCreatedAtDesc();
    List<StockMovement> findAllByItemIdOrderByCreatedAtDesc(Long itemId);
}
