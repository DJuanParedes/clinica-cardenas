package pe.clinicacardenas.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentRepository;
import pe.clinicacardenas.agenda.AppointmentStatus;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.billing.InvoiceRepository;
import pe.clinicacardenas.clinical.TreatmentItemRepository;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.user.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Resumen de inicio adaptado al rol. */
@RestController
public class DashboardController {

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final InvoiceRepository invoices;
    private final TreatmentItemRepository items;
    private final DentistRepository dentists;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public DashboardController(AppointmentRepository appointments, PatientRepository patients, InvoiceRepository invoices,
                               TreatmentItemRepository items, DentistRepository dentists, UserRepository users, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.patients = patients;
        this.invoices = invoices;
        this.items = items;
        this.dentists = dentists;
        this.users = users;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Authentication auth) {
        LocalDate today = Dates.today();
        LocalDateTime now = Dates.now();
        Long dentistId = users.findByUsernameIgnoreCase(auth.getName())
                .flatMap(u -> dentists.findByUserId(u.id)).map(d -> d.id).orElse(null);

        List<Appointment> todays = appointments.findInRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), dentistId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AppointmentStatus s : AppointmentStatus.values()) counts.put(s.name(), 0L);
        List<Appointment> upcoming = new ArrayList<>();
        List<Appointment> waiting = new ArrayList<>();
        for (Appointment a : todays) {
            counts.merge(a.status.name(), 1L, Long::sum);
            if (a.status == AppointmentStatus.EN_ESPERA || a.status == AppointmentStatus.EN_ATENCION) waiting.add(a);
            else if (!a.endAt.isBefore(now) && (a.status == AppointmentStatus.PROGRAMADA || a.status == AppointmentStatus.CONFIRMADA)
                    && upcoming.size() < 8) upcoming.add(a);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today);
        m.put("scopedToDentist", dentistId != null);
        m.put("appointmentsToday", todays.size());
        m.put("statusCounts", counts);
        m.put("upcoming", upcoming);
        m.put("waiting", waiting);
        m.put("patientsTotal", patients.countByActiveTrue());
        m.put("patientsThisMonth", patients.countByCreatedAtGreaterThanEqual(today.withDayOfMonth(1).atStartOfDay()));
        m.put("billedToday", invoices.sumTotal(today, today));
        m.put("billedMonth", invoices.sumTotal(today.withDayOfMonth(1), today));
        m.put("pendingSunat", invoices.countByStatus("GENERADO") + invoices.countByStatus("ERROR"));
        m.put("pendingTreatments", items.countByStatus("PENDIENTE") + items.countByStatus("EN_PROCESO"));
        // Caja (HU-13 / HU-14)
        m.put("collectedToday", jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where status = 'VALIDO' and paid_at >= ?",
                java.math.BigDecimal.class, today.atStartOfDay()));
        m.put("collectedMonth", jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where status = 'VALIDO' and paid_at >= ?",
                java.math.BigDecimal.class, today.withDayOfMonth(1).atStartOfDay()));
        m.put("receivable", jdbc.queryForObject("""
                select greatest(coalesce((select sum(price) from treatment_items where status = 'REALIZADO'), 0)
                              - coalesce((select sum(amount) from payments where status = 'VALIDO'), 0), 0)
                """, java.math.BigDecimal.class));
        // Mejoras: inventario bajo, satisfacción, pedidos de reprogramación y lista de espera
        m.put("lowStock", jdbc.queryForObject("select count(*) from inventory_items where active = true and stock <= min_stock", Long.class));
        m.put("satisfaction", jdbc.queryForObject("select coalesce(round(avg(score), 2), 0) from surveys where created_at >= ?",
                java.math.BigDecimal.class, today.minusDays(90).atStartOfDay()));
        m.put("surveysCount", jdbc.queryForObject("select count(*) from surveys where created_at >= ?", Long.class, today.minusDays(90).atStartOfDay()));
        m.put("rescheduleRequests", jdbc.queryForObject("""
                select count(*) from appointments where reschedule_requested_at is not null
                   and status in ('PROGRAMADA', 'CONFIRMADA') and start_at > now()
                """, Long.class));
        m.put("waitlist", jdbc.queryForObject("select count(*) from waitlist where status in ('ACTIVA', 'AVISADA')", Long.class));
        return m;
    }
}
