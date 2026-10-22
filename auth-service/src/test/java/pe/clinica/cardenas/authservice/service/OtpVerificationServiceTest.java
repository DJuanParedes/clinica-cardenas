package pe.clinica.cardenas.authservice.service;

import org.junit.jupiter.api.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import pe.clinica.cardenas.authservice.domain.RecoveryChannel;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpVerificationServiceTest {
    OtpVerificationService service;
    final Instant start = Instant.parse("2026-10-22T12:00:00Z");
    @BeforeEach void setup() {
        service = new OtpVerificationService(mock(JavaMailSender.class));
        ReflectionTestUtils.setField(service, "provider", "local");
        ReflectionTestUtils.setField(service, "localCode", "123456");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(start, ZoneOffset.UTC));
    }
    @Test void codeRequiresRequestAndIsSingleUse() {
        assertFalse(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
        service.start("+51900000001", RecoveryChannel.SMS);
        assertTrue(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
        assertFalse(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
    }
    @Test void codeExpiresAtTenMinutes() {
        service.start("+51900000001", RecoveryChannel.SMS);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(start.plusSeconds(600), ZoneOffset.UTC));
        assertFalse(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
    }
    @Test void fiveFailuresInvalidateChallenge() {
        service.start("+51900000001", RecoveryChannel.SMS);
        for (int i=0;i<5;i++) assertFalse(service.check("+51900000001", RecoveryChannel.SMS, "000000"));
        assertFalse(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
    }
    @Test void destinationAndChannelAreIsolated() {
        service.start("+51900000001", RecoveryChannel.SMS);
        assertFalse(service.check("+51900000002", RecoveryChannel.SMS, "123456"));
        assertFalse(service.check("+51900000001", RecoveryChannel.WHATSAPP, "123456"));
        assertTrue(service.check("+51900000001", RecoveryChannel.SMS, "123456"));
    }
    @Test void concurrentChecksConsumeOnlyOnce() throws Exception {
        service.start("+51900000001", RecoveryChannel.SMS);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Boolean> check=()->{gate.await();return service.check("+51900000001",RecoveryChannel.SMS,"123456");};
            var a=pool.submit(check);var b=pool.submit(check);gate.countDown();
            assertNotEquals(a.get(),b.get());
        }
    }
}
