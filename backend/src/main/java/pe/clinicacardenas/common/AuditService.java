package pe.clinicacardenas.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import pe.clinicacardenas.security.CurrentUser;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String entity, Long entityId, String action, String detail) {
        AuditLog a = new AuditLog();
        a.entity = entity;
        a.entityId = entityId;
        a.action = action;
        a.detail = detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail;
        a.username = CurrentUser.usernameOrSystem();
        a.createdAt = Dates.now();
        repo.save(a);
    }

    public List<AuditLog> forEntity(String entity, Long id) {
        return repo.findByEntityAndEntityIdOrderByCreatedAtDesc(entity, id);
    }

    public List<AuditLog> latest(int size) {
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size));
    }
}
