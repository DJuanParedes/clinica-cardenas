package pe.clinica.cardenas.encounterservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.ProformaHistory;
import java.util.List;

public interface ProformaHistoryRepository extends JpaRepository<ProformaHistory,Long> {
    List<ProformaHistory> findAllByProformaIdOrderByCreatedAtDesc(Long proformaId);
}
