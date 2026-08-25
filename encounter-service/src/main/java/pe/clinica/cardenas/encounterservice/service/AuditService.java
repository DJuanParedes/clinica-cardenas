package pe.clinica.cardenas.encounterservice.service;
import com.fasterxml.jackson.databind.ObjectMapper; import lombok.RequiredArgsConstructor; import org.springframework.security.core.Authentication; import org.springframework.stereotype.Service; import pe.clinica.cardenas.encounterservice.domain.AuditEntry; import pe.clinica.cardenas.encounterservice.repository.AuditEntryRepository; import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class AuditService { private final AuditEntryRepository repository; private final ObjectMapper mapper;
 public void log(String module,String entity,String id,String action,Authentication auth,Object before,Object after,String reason){try{repository.save(AuditEntry.builder().module(module).entityType(entity).entityId(id).action(action).actor(auth==null?"SYSTEM":auth.getName()).actorRole(auth==null?"SYSTEM":auth.getAuthorities().stream().findFirst().map(Object::toString).orElse("UNKNOWN")).beforeJson(before==null?null:mapper.writeValueAsString(before)).afterJson(after==null?null:mapper.writeValueAsString(after)).reason(reason).occurredAt(LocalDateTime.now()).build());}catch(Exception ignored){}}
}
