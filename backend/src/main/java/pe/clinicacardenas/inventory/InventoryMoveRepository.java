package pe.clinicacardenas.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMoveRepository extends JpaRepository<InventoryMove, Long> {
    List<InventoryMove> findTop50ByItemIdOrderByIdDesc(Long itemId);

    List<InventoryMove> findTop100ByOrderByIdDesc();

    boolean existsByTreatmentItemIdAndKind(Long treatmentItemId, String kind);
}
