package pe.clinicacardenas.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityAndEntityIdOrderByCreatedAtDesc(String entity, Long entityId);
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable page);
}
