package pe.clinica.cardenas.authservice.web;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.clinica.cardenas.authservice.domain.*;
import pe.clinica.cardenas.authservice.repository.UserAccountRepository;
import pe.clinica.cardenas.authservice.service.*;
import pe.clinica.cardenas.security.TenantContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SprintOneSecurityTest {
    AnnotationConfigApplicationContext context;
    UserAdminController controller;
    UserAccountRepository users;
    SecurityAuditService audit;
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean UserAccountRepository users(){return mock(UserAccountRepository.class);}
        @Bean PasswordEncoder encoder(){return mock(PasswordEncoder.class);}
        @Bean SchedulingProvisioningClient scheduling(){return mock(SchedulingProvisioningClient.class);}
        @Bean SecurityAuditService audit(){return mock(SecurityAuditService.class);}
        @Bean UserAdminController controller(UserAccountRepository u,PasswordEncoder p,SchedulingProvisioningClient s,SecurityAuditService a){return new UserAdminController(u,p,s,a);}
    }
    @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(UserAdminController.class);users=context.getBean(UserAccountRepository.class);audit=context.getBean(SecurityAuditService.class);TenantContext.setClinicId("CARDENAS");}
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContext.clear();context.close();}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.test","unused",AuthorityUtils.createAuthorityList("ROLE_"+role)));}
    @Test void receptionAndDoctorCannotManageUsersOrReadAudit(){
        for(String role:List.of("RECEPCION","DOCTOR")){as(role);assertThrows(AccessDeniedException.class,()->controller.list());assertThrows(AccessDeniedException.class,()->controller.audit());}
        verifyNoInteractions(users,audit);
    }
    @Test void adminCanReadAudit(){as("ADMIN");when(audit.recent()).thenReturn(List.of());assertTrue(controller.audit().isEmpty());verify(audit).recent();}
    @Test void roleChangeRevokesSessionAndRecordsOldAndNewRole(){
        as("ADMIN");var user=UserAccount.builder().id(7L).email("staff@example.test").fullName("Prueba").dni("90000001").role(Role.RECEPCION).clinicId("CARDENAS").enabled(true).sessionVersion(2).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));when(users.save(any())).thenAnswer(i->i.getArgument(0));
        var request=new UserAdminController.UpdateUserRequest("Prueba","staff@example.test","90000001","+51900000001",RecoveryChannel.SMS,Role.DOCTOR,true,"General","Principal","08:00","18:00");
        controller.update(7L,request,new MockHttpServletRequest());
        assertEquals(Role.DOCTOR,user.getRole());assertEquals(3,user.getSessionVersion());assertNotNull(user.getSessionInvalidatedAt());
        verify(audit).record("USER_UPDATED",user,Role.RECEPCION);
    }
    @Test void anotherClinicUserCannotBeChanged(){as("ADMIN");when(users.findById(7L)).thenReturn(Optional.of(UserAccount.builder().id(7L).clinicId("OTHER").role(Role.RECEPCION).build()));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.setEnabled(7L,false,new MockHttpServletRequest()));verify(users,never()).save(any());}
}
