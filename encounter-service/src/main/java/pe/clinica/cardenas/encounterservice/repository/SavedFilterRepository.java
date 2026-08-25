package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.SavedFilter;
public interface SavedFilterRepository extends JpaRepository<SavedFilter,Long> {
    java.util.List<SavedFilter> findAllByOwnerEmailOrSharedTrueOrderByCreatedAtDesc(String ownerEmail);
}
