package pe.clinica.cardenas.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.clinica.cardenas.authservice.domain.SecurityAuditEntry;
import java.util.List;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEntry, Long> {
    List<SecurityAuditEntry> findTop100ByClinicIdOrderByOccurredAtDescIdDesc(String clinicId);
}
