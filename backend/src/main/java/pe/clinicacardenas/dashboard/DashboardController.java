package pe.clinicacardenas.dashboard;

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

    public DashboardController(AppointmentRepository appointments, PatientRepository patients, InvoiceRepository invoices,
                               TreatmentItemRepository items, DentistRepository dentists, UserRepository users) {
        this.appointments = appointments;
        this.patients = patients;
        this.invoices = invoices;
        this.items = items;
        this.dentists = dentists;
        this.users = users;
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
        return m;
    }
}
