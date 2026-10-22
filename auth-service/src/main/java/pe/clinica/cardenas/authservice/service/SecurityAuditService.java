package pe.clinica.cardenas.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.clinica.cardenas.authservice.domain.*;
import pe.clinica.cardenas.authservice.repository.SecurityAuditRepository;
import pe.clinica.cardenas.security.TenantContext;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    private final SecurityAuditRepository repository;

    public void record(String action, UserAccount account, Role previousRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "ANONYMOUS" : authentication.getName();
        repository.save(SecurityAuditEntry.builder().clinicId(account.getClinicId())
                .occurredAt(Instant.now()).actor(actor).action(action).userId(account.getId())
                .previousRole(previousRole == null ? null : previousRole.name())
                .newRole(account.getRole().name()).build());
    }

    public List<SecurityAuditEntry> recent() {
        return repository.findTop100ByClinicIdOrderByOccurredAtDescIdDesc(TenantContext.currentClinicId());
    }
}
