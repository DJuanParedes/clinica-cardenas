package pe.clinica.cardenas.financeservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.financeservice.domain.*;
import pe.clinica.cardenas.financeservice.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final DoctorCommissionRepository commissions;
    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final CashSessionRepository cashSessions;
    private final ExpenseRepository expenses;

    @Bean
    CommandLineRunner seedFinance() {
        return args -> {
            if (commissions.count() == 0) {
                commissions.save(DoctorCommission.builder().doctorName("Dra. Valeria Cárdenas").period("2026-08").grossProduction(new BigDecimal("18750.00")).collectedProduction(new BigDecimal("15200.00")).commissionRate(new BigDecimal("35.00")).laboratoryCosts(new BigDecimal("980.00")).finalAmount(new BigDecimal("4340.00")).paymentStatus("EN REVISIÓN").build());
                commissions.save(DoctorCommission.builder().doctorName("Dr. Diego Salazar").period("2026-08").grossProduction(new BigDecimal("14300.00")).collectedProduction(new BigDecimal("12100.00")).commissionRate(new BigDecimal("32.00")).laboratoryCosts(new BigDecimal("350.00")).finalAmount(new BigDecimal("3522.00")).paymentStatus("CALCULADO").build());
            }
            if (memberships.count() == 0) {
                memberships.save(Membership.builder().patientName("Familia Torres Díaz").planName("Plan Familiar Preventivo").totalSessions(12).usedSessions(4).price(new BigDecimal("1400.00")).paidAmount(new BigDecimal("900.00")).startDate(LocalDate.now().minusMonths(2)).endDate(LocalDate.now().plusMonths(10)).membershipStatus("ACTIVA").build());
            }
            if (payments.count() == 0) {
                payments.save(Payment.builder().budgetId(1L).patientName("Andrea Flores Mendoza").concept("Adelanto de rehabilitación oral").amount(new BigDecimal("650.00")).paymentMethod("TARJETA").reference("POS-260805-001").paymentStatus("REGISTRADO").registeredBy("finanzas@cardenas.pe").paidAt(LocalDateTime.now().minusHours(2)).build());
                payments.save(Payment.builder().patientName("José Quispe Ramos").concept("Consulta y radiografía").amount(new BigDecimal("180.00")).paymentMethod("YAPE").reference("YAPE-884211").paymentStatus("REGISTRADO").registeredBy("recepcion@cardenas.pe").paidAt(LocalDateTime.now().minusHours(1)).build());
            }
            if (cashSessions.count() == 0) {
                cashSessions.save(CashSession.builder().branch("Sede principal").cashier("finanzas@cardenas.pe").openingAmount(new BigDecimal("200.00")).expectedAmount(new BigDecimal("1030.00")).actualAmount(new BigDecimal("1030.00")).difference(BigDecimal.ZERO).openedAt(LocalDateTime.now().minusDays(1).withHour(8).withMinute(0)).closedAt(LocalDateTime.now().minusDays(1).withHour(18).withMinute(0)).cashStatus("CERRADA").notes("Cierre conforme").build());
            }
            if (expenses.count() == 0) {
                expenses.save(Expense.builder().category("INSUMOS").description("Compra de material de bioseguridad").amount(new BigDecimal("420.00")).supplier("Dental Supply SAC").documentNumber("F001-00982").registeredBy("finanzas@cardenas.pe").registeredAt(LocalDateTime.now().minusDays(1)).expenseStatus("APROBADO").build());
            }
        };
    }
}
