package pe.clinicacardenas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.cash.Expense;
import pe.clinicacardenas.cash.ExpenseRepository;
import pe.clinicacardenas.cash.Payment;
import pe.clinicacardenas.cash.PaymentRepository;
import pe.clinicacardenas.clinical.*;
import pe.clinicacardenas.commission.CommissionService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.patient.PatientService;
import pe.clinicacardenas.patient.PatientValidator;
import pe.clinicacardenas.portal.PatientAccount;
import pe.clinicacardenas.portal.PatientAccountRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Historia de operación de los últimos 4 meses para que caja, comisiones e indicadores tengan datos reales
 * que mostrar: citas atendidas y canceladas, procedimientos realizados, cobros, egresos, reglas de comisión,
 * liquidaciones y el acceso a la app de una paciente. Datos ficticios, generados con semilla fija.
 */
@Component
public class HistorySeeder {

    private static final Logger log = LoggerFactory.getLogger(HistorySeeder.class);
    public static final String PATIENT_APP_PASSWORD = "Paciente2026";

    private final PatientService patientService;
    private final PatientRepository patients;
    private final DentistRepository dentists;
    private final AppointmentRepository appointments;
    private final TreatmentPlanRepository plans;
    private final TreatmentItemRepository items;
    private final ProcedureRepository procedures;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final CommissionService commissions;
    private final PatientAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final Random rnd = new Random(2026);

    public HistorySeeder(PatientService patientService, PatientRepository patients, DentistRepository dentists,
                         AppointmentRepository appointments, TreatmentPlanRepository plans, TreatmentItemRepository items,
                         ProcedureRepository procedures, PaymentRepository payments, ExpenseRepository expenses,
                         CommissionService commissions, PatientAccountRepository accounts, PasswordEncoder encoder) {
        this.patientService = patientService;
        this.patients = patients;
        this.dentists = dentists;
        this.appointments = appointments;
        this.plans = plans;
        this.items = items;
        this.procedures = procedures;
        this.payments = payments;
        this.expenses = expenses;
        this.commissions = commissions;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    private void addPatient(String dni, String first, String last, String birth, String sex, String phone, String email, String district) {
        if (patients.findByDocTypeAndDocNumber("DNI", dni).isPresent()) return;
        patientService.createDemo(new PatientValidator.Input("DNI", dni, first, last, LocalDate.parse(birth), sex, phone,
                email, null, district, null, null, null));
    }

    private <T> T pick(List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static BigDecimal m(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void seed() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        try {
            run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void run() {
        addPatient("43215678", "Miguel Ángel", "Huamaní Castro", "1983-04-18", "M", "945112233", "mhuamani@gmail.com", "Chaclacayo");
        addPatient("71548236", "Lucero", "Pariona Quispe", "1996-08-25", "F", "951223344", null, "Chaclacayo");
        addPatient("44875123", "Jorge Luis", "Espinoza Ramos", "1979-12-02", "M", "962334455", "jespinoza@outlook.com", "Lurigancho-Chosica");
        addPatient("76321458", "Daniela", "Vargas Ccahuana", "2003-06-11", "F", "973445566", "dani.vargas@gmail.com", "Ate");
        addPatient("42563987", "Patricia", "Mendoza Llanos", "1975-01-29", "F", "984556677", null, "Chaclacayo");
        addPatient("70985632", "Renzo", "Salazar Ticona", "1993-10-07", "M", "995667788", "renzo.salazar@gmail.com", "Santa Eulalia");
        addPatient("45698712", "Gladys", "Cóndor Yupanqui", "1968-03-15", "F", "916778899", null, "Chaclacayo");
        addPatient("72145896", "Fernando", "Aguilar Paucar", "1999-09-23", "M", "927889900", "faguilar@gmail.com", "Ricardo Palma");
        addPatient("48523691", "Silvia", "Rojas Palomino", "1988-05-05", "F", "938990011", "srojas@hotmail.com", "Chaclacayo");
        addPatient("74123659", "Kevin", "Inga Mallqui", "2007-02-14", "M", "949001122", null, "Lurigancho-Chosica");

        List<Patient> ps = patients.findAll();
        List<Dentist> ds = dentists.findAvailable();
        if (ds.size() < 2 || ps.isEmpty()) return;
        Map<String, Procedure> proc = new HashMap<>();
        for (Procedure p : procedures.findAll()) proc.put(p.code, p);

        // Procedimientos más frecuentes por especialidad (con repetición para dar peso).
        Map<Long, List<String>> menu = new HashMap<>();
        for (Dentist d : ds) {
            boolean ortho = d.specialty.toLowerCase().contains("ortodon");
            menu.put(d.id, ortho
                    ? List.of("ORT-001", "ORT-001", "ORT-001", "CON-001", "PRE-001", "PRE-001", "OPE-001", "PRE-002", "EST-001", "RAD-002")
                    : List.of("CON-001", "RAD-001", "RAD-001", "END-001", "END-002", "OPE-002", "OPE-001", "CIR-001", "CIR-002", "PRE-001", "PER-001"));
        }
        String[] reasonsByCode = {"Control", "Evaluación", "Tratamiento programado", "Seguimiento"};
        List<String> cancelReasons = List.of("Viaje del paciente", "Motivos de trabajo", "Paciente enfermo", "Problema de transporte",
                "Reprogramará por teléfono", "Odontólogo en congreso");
        List<String> methods = List.of("EFECTIVO", "EFECTIVO", "EFECTIVO", "YAPE", "YAPE", "YAPE", "TARJETA", "TARJETA", "PLIN", "TRANSFERENCIA");

        // Un plan histórico por paciente y odontólogo.
        Map<String, TreatmentPlan> planOf = new HashMap<>();
        LocalDate today = Dates.today();
        LocalDate start = today.minusDays(120);
        int[][] slots = {{9, 0}, {10, 0}, {11, 30}, {15, 0}, {16, 0}, {17, 0}};
        int created = 0;
        for (LocalDate day = start; day.isBefore(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            int n = day.getDayOfWeek() == DayOfWeek.SATURDAY ? 2 : 3 + rnd.nextInt(4);
            for (int s = 0; s < n && s < slots.length; s++) {
                Dentist d = ds.get((s + day.getDayOfMonth()) % ds.size());
                Patient p = pick(ps);
                LocalDateTime at = day.atTime(slots[s][0], slots[s][1]);
                int roll = rnd.nextInt(100);
                AppointmentStatus st = roll < 78 ? AppointmentStatus.ATENDIDA : roll < 89 ? AppointmentStatus.CANCELADA : AppointmentStatus.NO_ASISTIO;
                String code = pick(menu.get(d.id));
                Procedure pr = proc.get(code);
                Appointment a = new Appointment();
                a.patient = p;
                a.dentist = d;
                a.startAt = at;
                a.durationMin = code.startsWith("END") || code.startsWith("CIR-002") || code.startsWith("EST") ? 60 : 30;
                a.endAt = at.plusMinutes(a.durationMin);
                a.reason = (pr == null ? reasonsByCode[rnd.nextInt(reasonsByCode.length)] : pr.name);
                a.status = st;
                a.createdAt = at.minusDays(2 + rnd.nextInt(6));
                a.createdBy = "recepcion";
                if (st == AppointmentStatus.CANCELADA) a.statusReason = pick(cancelReasons);
                if (st == AppointmentStatus.ATENDIDA) a.arrivedAt = at.minusMinutes(rnd.nextInt(12));
                appointments.save(a);
                created++;
                if (st != AppointmentStatus.ATENDIDA || pr == null) continue;

                TreatmentPlan plan = planOf.computeIfAbsent(p.id + ":" + d.id, k -> {
                    TreatmentPlan tp = new TreatmentPlan();
                    tp.patientId = p.id;
                    tp.dentist = d;
                    tp.title = d.specialty.toLowerCase().contains("ortodon") ? "Tratamiento de ortodoncia y prevención" : "Tratamiento integral";
                    tp.status = "ACTIVO";
                    tp.createdAt = at.minusDays(1);
                    tp.createdBy = d.user.username;
                    return plans.save(tp);
                });
                TreatmentItem it = new TreatmentItem();
                it.planId = plan.id;
                it.procedure = pr;
                it.price = pr.price;
                it.status = "REALIZADO";
                it.doneAt = at.plusMinutes(a.durationMin - 5);
                it.doneByDentistId = d.id;
                it.sortOrder = 100 + created;
                items.save(it);

                // Cobro: la mayoría paga al terminar; algunos pagan la mitad y otros quedan debiendo.
                int pay = rnd.nextInt(100);
                if (pay < 82) addPayment(p.id, it.id, it.price, pick(methods), it.doneAt.plusMinutes(8));
                else if (pay < 92) addPayment(p.id, it.id, it.price.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP), pick(methods), it.doneAt.plusMinutes(8));
            }
        }
        // Algún procedimiento pendiente en planes activos (para el indicador de pendientes).
        for (TreatmentPlan tp : planOf.values()) {
            if (rnd.nextInt(100) < 45) {
                TreatmentItem it = new TreatmentItem();
                it.planId = tp.id;
                List<String> codes = menu.get(tp.dentist.id);
                it.procedure = proc.get(pick(codes));
                it.price = it.procedure.price;
                it.status = rnd.nextInt(3) == 0 ? "EN_PROCESO" : "PENDIENTE";
                it.sortOrder = 999;
                items.save(it);
            } else if (rnd.nextInt(100) < 50) {
                tp.status = "COMPLETADO";
                plans.save(tp);
            }
        }
        // Un par de pagos a cuenta.
        addPayment(ps.get(1).id, null, m(200), "YAPE", today.minusDays(9).atTime(12, 10));
        addPayment(ps.get(3).id, null, m(150), "EFECTIVO", today.minusDays(3).atTime(16, 40));

        seedExpenses(today);
        seedCommissions(ds, today);

        // Acceso a la app para María Fernanda Quispe (DNI 45872196) y avisos por WhatsApp y correo.
        patients.findByDocTypeAndDocNumber("DNI", "45872196").ifPresent(p -> {
            p.notifyChannel = "AMBOS";
            patients.save(p);
            PatientAccount acc = new PatientAccount();
            acc.patientId = p.id;
            acc.passwordHash = encoder.encode(PATIENT_APP_PASSWORD);
            acc.active = true;
            acc.createdAt = Dates.now();
            acc.activatedAt = Dates.now();
            accounts.save(acc);
        });
        log.info("Historia de demostración: {} citas pasadas, cobros, egresos y comisiones", created);
    }

    private void addPayment(Long patientId, Long itemId, BigDecimal amount, String method, LocalDateTime at) {
        Payment p = new Payment();
        p.patientId = patientId;
        p.treatmentItemId = itemId;
        p.amount = amount;
        p.method = method;
        p.reference = "EFECTIVO".equals(method) ? null : String.valueOf(100000 + rnd.nextInt(899999));
        p.paidAt = at;
        p.cashier = "caja";
        p.status = "VALIDO";
        payments.save(p);
    }

    private void addExpense(LocalDate date, String cat, String concept, double amount, String method, String supplier) {
        if (date.isAfter(Dates.today())) return;
        Expense x = new Expense();
        x.expenseDate = date;
        x.category = cat;
        x.concept = concept;
        x.amount = m(amount);
        x.method = method;
        x.reference = "EFECTIVO".equals(method) ? null : String.valueOf(200000 + rnd.nextInt(799999));
        x.supplier = supplier;
        x.status = "VALIDO";
        x.createdAt = date.atTime(18, 0);
        x.createdBy = "caja";
        expenses.save(x);
    }

    private void seedExpenses(LocalDate today) {
        for (int k = 4; k >= 0; k--) {
            LocalDate month = today.minusMonths(k).withDayOfMonth(1);
            addExpense(month.plusDays(1), "ALQUILER", "Alquiler del local · Urb. La Floresta", 2500, "TRANSFERENCIA", "Inmobiliaria Los Cóndores S.A.C.");
            addExpense(month.plusDays(9), "SERVICIOS", "Luz, agua e internet", 380 + rnd.nextInt(90), "TRANSFERENCIA", "Luz del Sur / Sedapal / Movistar");
            addExpense(month.plusDays(4), "INSUMOS", "Resinas, anestésicos y descartables", 650 + rnd.nextInt(500), "TARJETA", "Dental Perú Import");
            addExpense(month.plusDays(18), "INSUMOS", "Guantes, mascarillas y material de esterilización", 280 + rnd.nextInt(200), "EFECTIVO", "Distribuidora Médica Chosica");
            addExpense(month.plusDays(12), "LABORATORIO", "Coronas y prótesis", 420 + rnd.nextInt(700), "TRANSFERENCIA", "Laboratorio Dental Sonrisa");
            addExpense(month.plusDays(27), "PLANILLA", "Remuneración de asistente dental", 1300, "TRANSFERENCIA", null);
            if (k % 2 == 0) addExpense(month.plusDays(15), "MANTENIMIENTO", "Mantenimiento de autoclave y compresor", 220 + rnd.nextInt(150), "EFECTIVO", "Servitec Dental");
            if (k == 1) addExpense(month.plusDays(20), "MARKETING", "Publicidad en Facebook e Instagram", 300, "TARJETA", "Meta Platforms");
        }
    }

    private boolean hasLines(Long dentistId, LocalDate from, LocalDate to) {
        Object lines = commissions.calculate(dentistId, from, to).get("lines");
        return lines instanceof List<?> l && !l.isEmpty();
    }

    private void seedCommissions(List<Dentist> ds, LocalDate today) {
        LocalDate since = today.minusDays(180);
        for (Dentist d : ds) {
            boolean ortho = d.specialty.toLowerCase().contains("ortodon");
            if (ortho) {
                commissions.saveRule(null, new CommissionService.RuleRequest(d.id, null, "PORCENTAJE", m(40), "REALIZADO", since, null, true,
                        "Acuerdo general 2026"));
                procedures.findAll().stream().filter(p -> "ORT-001".equals(p.code)).findFirst().ifPresent(p ->
                        commissions.saveRule(null, new CommissionService.RuleRequest(d.id, p.id, "MONTO_FIJO", m(65), "REALIZADO", since, null, true,
                                "Monto fijo por control de ortodoncia")));
            } else {
                commissions.saveRule(null, new CommissionService.RuleRequest(d.id, null, "PORCENTAJE", m(35), "COBRADO", since, null, true,
                        "Se paga sobre lo efectivamente cobrado"));
                procedures.findAll().stream().filter(p -> "END-002".equals(p.code)).findFirst().ifPresent(p ->
                        commissions.saveRule(null, new CommissionService.RuleRequest(d.id, p.id, "PORCENTAJE", m(45), "COBRADO", since, null, true,
                                "Endodoncias multirradiculares")));
            }
        }
        // Liquidaciones de los dos meses anteriores: la más antigua pagada; la del mes pasado, pendiente para uno.
        LocalDate prev = today.minusMonths(1).withDayOfMonth(1);
        LocalDate prevEnd = prev.plusMonths(1).minusDays(1);
        LocalDate prev2 = today.minusMonths(2).withDayOfMonth(1);
        LocalDate prev2End = prev2.plusMonths(1).minusDays(1);
        // Las excepciones dentro de servicios transaccionales marcarían toda la carga para revertirse:
        // por eso se verifica antes que haya comisiones por liquidar.
        for (Dentist d : ds) {
            if (!hasLines(d.id, prev2, prev2End)) continue;
            Map<String, Object> s2 = commissions.settle(new CommissionService.SettleRequest(d.id, prev2, prev2End, null, "Liquidación mensual"));
            var st2 = (pe.clinicacardenas.commission.CommissionSettlement) s2.get("settlement");
            commissions.pay(st2.id, new CommissionService.PayoutRequest(st2.totalAmount, null, "TRANSFERENCIA", "OP" + (500000 + rnd.nextInt(99999))));
        }
        Dentist first = ds.get(0);
        if (hasLines(first.id, prev, prevEnd)) {
            Map<String, Object> s1 = commissions.settle(new CommissionService.SettleRequest(first.id, prev, prevEnd, null, "Liquidación mensual"));
            var st1 = (pe.clinicacardenas.commission.CommissionSettlement) s1.get("settlement");
            BigDecimal half = st1.totalAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            if (half.signum() > 0) commissions.pay(st1.id, new CommissionService.PayoutRequest(half, null, "EFECTIVO", null));
        }
    }
}
