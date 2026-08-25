package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.TreatmentCatalog;
public interface TreatmentCatalogRepository extends JpaRepository<TreatmentCatalog,Long> {
    java.util.List<TreatmentCatalog> findAllByActiveTrueOrderByNameAsc();
}
