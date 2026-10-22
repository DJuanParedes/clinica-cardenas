package pe.clinica.cardenas.authservice.web;

import org.junit.jupiter.api.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.authservice.domain.*;
import pe.clinica.cardenas.authservice.repository.*;
import pe.clinica.cardenas.authservice.service.*;
import pe.clinica.cardenas.security.JwtService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetTest {
    UserAccountRepository users=mock(UserAccountRepository.class);
    PasswordEncoder encoder=mock(PasswordEncoder.class);
    OtpVerificationService otp=mock(OtpVerificationService.class);
    SecurityAuditService audit=mock(SecurityAuditService.class);
    ClinicRepository clinics=mock(ClinicRepository.class);
    JwtService jwt=mock(JwtService.class);
    AuthController controller=new AuthController(users,clinics,encoder,jwt,otp,audit,mock(Environment.class));
    UserAccount account;
    @BeforeEach void setup(){account=UserAccount.builder().id(1L).email("staff@example.test").role(Role.RECEPCION).enabled(true).recoveryChannel(RecoveryChannel.EMAIL).sessionVersion(4).passwordHash("oldHash").build();when(users.findByEmail(account.getEmail())).thenReturn(Optional.of(account));}
    @Test void existingUnknownAndDisabledAccountsHaveSamePublicResponse(){
        var known=controller.requestPasswordReset(new AuthController.ResetRequest(account.getEmail()));
        var unknown=controller.requestPasswordReset(new AuthController.ResetRequest("missing@example.test"));
        account.setEnabled(false);var disabled=controller.requestPasswordReset(new AuthController.ResetRequest(account.getEmail()));
        assertEquals(known,unknown);assertEquals(known,disabled);assertNull(known.destination());assertNull(known.channel());
        verify(audit).record("PASSWORD_RESET_REQUESTED",account,null);
    }
    @Test void invalidCodesLockAfterFiveFailuresAndAreAudited(){
        var req=new AuthController.ResetConfirmRequest(account.getEmail(),"000000","NewPassword1!");
        for(int i=0;i<5;i++)assertThrows(ResponseStatusException.class,()->controller.confirmPasswordReset(req));
        assertNotNull(account.getPasswordResetLockedUntil());
        assertThrows(ResponseStatusException.class,()->controller.confirmPasswordReset(req));
        verify(otp,times(5)).check(anyString(),any(),anyString());verify(audit,times(5)).record("PASSWORD_RESET_REJECTED",account,null);
    }
    @Test void acceptedResetRevokesExistingSessions(){
        when(otp.check(anyString(),any(),eq("123456"))).thenReturn(true);when(encoder.encode("NewPassword1!")).thenReturn("newHash");
        controller.confirmPasswordReset(new AuthController.ResetConfirmRequest(account.getEmail(),"123456","NewPassword1!"));
        assertEquals("newHash",account.getPasswordHash());assertEquals(5,account.getSessionVersion());assertNotNull(account.getPasswordChangedAt());verify(audit).record("PASSWORD_RESET_COMPLETED",account,null);
    }
    @Test void previouslyIssuedSessionIsRejectedAfterRoleOrVersionChanges(){
        var clinic=mock(Clinic.class);when(clinic.getCode()).thenReturn("CARDENAS");when(clinic.isActive()).thenReturn(true);
        when(clinics.findByCodeIgnoreCase("CARDENAS")).thenReturn(Optional.of(clinic));
        var claims=io.jsonwebtoken.Jwts.claims().subject(account.getEmail()).add("role","RECEPCION")
                .add("clinicId","CARDENAS").add("platformAdmin",false).add("sv",4).build();
        when(jwt.parse("old-token")).thenReturn(claims);
        assertDoesNotThrow(()->controller.validateSession("Bearer old-token"));
        account.setSessionVersion(5);
        assertEquals(401,assertThrows(ResponseStatusException.class,()->controller.validateSession("Bearer old-token")).getStatusCode().value());
        account.setSessionVersion(4);account.setRole(Role.DOCTOR);
        assertEquals(401,assertThrows(ResponseStatusException.class,()->controller.validateSession("Bearer old-token")).getStatusCode().value());
    }
}
