package pe.clinica.cardenas.encounterservice.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.encounterservice.domain.AuditEntry;
public interface AuditEntryRepository extends JpaRepository<AuditEntry,Long> {
    java.util.List<AuditEntry> findTop500ByOrderByOccurredAtDesc();
}
