package pe.clinica.cardenas.authservice.service;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import pe.clinica.cardenas.authservice.domain.*;
import pe.clinica.cardenas.authservice.repository.SecurityAuditRepository;
import pe.clinica.cardenas.security.TenantContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityAuditServiceTest {
    @Test void recoveryUsesAccountClinicAndQueriesUseActiveClinic(){
        var repo=mock(SecurityAuditRepository.class);var service=new SecurityAuditService(repo);
        var user=UserAccount.builder().id(7L).clinicId("OTHER").role(Role.DOCTOR).passwordHash("not recorded").build();
        service.record("PASSWORD_RESET_COMPLETED",user,null);
        var captured=ArgumentCaptor.forClass(SecurityAuditEntry.class);verify(repo).save(captured.capture());
        assertEquals("OTHER",captured.getValue().getClinicId());assertEquals(7L,captured.getValue().getUserId());assertNotNull(captured.getValue().getOccurredAt());
        TenantContext.setClinicId("CARDENAS");try{service.recent();verify(repo).findTop100ByClinicIdOrderByOccurredAtDescIdDesc("CARDENAS");}finally{TenantContext.clear();}
    }
}
