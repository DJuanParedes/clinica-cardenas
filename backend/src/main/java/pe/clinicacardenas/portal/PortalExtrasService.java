package pe.clinicacardenas.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.billing.BillingService;
import pe.clinicacardenas.billing.Invoice;
import pe.clinicacardenas.billing.InvoiceItemRepository;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Funciones de la app del paciente: reservar, reprogramar y cancelar citas; avisos; pagos en línea (pasarela
 * simulada) con boleta electrónica; documentos compartidos y encuesta de satisfacción.
 */
@Service
public class PortalExtrasService {
    private static final String AMOUNT = "amount";
    private static final String DURATIONMIN = "durationMin";
    private static final String LABEL = "label";
    private static final String MESSAGE = "message";
    private static final String SLOTS = "slots";
    private static final String STARTAT = "startAt";


    private static final Logger log = LoggerFactory.getLogger(PortalExtrasService.class);

    /** Motivos que el paciente puede elegir al reservar, con su duración. */
    public static final List<Map<String, Object>> REASONS = List.of(
            Map.of("code", "EVALUACION", LABEL, "Evaluación general", DURATIONMIN, 30),
            Map.of("code", "LIMPIEZA", LABEL, "Limpieza dental", DURATIONMIN, 45),
            Map.of("code", "DOLOR", LABEL, "Dolor o molestia", DURATIONMIN, 30),
            Map.of("code", "ORTODONCIA", LABEL, "Control de ortodoncia", DURATIONMIN, 30),
            Map.of("code", "BLANQUEAMIENTO", LABEL, "Blanqueamiento", DURATIONMIN, 60),
            Map.of("code", "CONTROL", LABEL, "Control de tratamiento", DURATIONMIN, 30));

    static final int MIN_HOURS_TO_BOOK = 2;
    static final int MIN_HOURS_TO_CHANGE = 12;
    static final int MAX_DAYS_AHEAD = 60;
    static final int MAX_OPEN = 2;

    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final AgendaService agenda;
    private final PatientNoticeRepository notices;
    private final SurveyRepository surveys;
    private final SimulatedPaymentGateway gateway;
    private final CashService cash;
    private final BillingService billing;
    private final InvoiceItemRepository invoiceItems;
    private final FileService files;
    private final AuditService audit;

    public PortalExtrasService(PatientRepository patients, AppointmentRepository appointments, AgendaService agenda,
                               PatientNoticeRepository notices, SurveyRepository surveys, SimulatedPaymentGateway gateway,
                               CashService cash, BillingService billing, InvoiceItemRepository invoiceItems,
                               FileService files, AuditService audit) {
        this.patients = patients;
        this.appointments = appointments;
        this.agenda = agenda;
        this.notices = notices;
        this.surveys = surveys;
        this.gateway = gateway;
        this.cash = cash;
        this.billing = billing;
        this.invoiceItems = invoiceItems;
        this.files = files;
        this.audit = audit;
    }

    public record BookRequest(Long dentistId, LocalDateTime startAt, String reasonCode, String notes) {}

    public record RescheduleRequest(Long dentistId, LocalDateTime startAt) {}

    public record CancelRequest(String reason) {}

    public record PayRequest(BigDecimal amount, String method, String approvalCode, String cardNumber,
                             String cardExpiry, String cardCvv) {}

    public record SurveyRequest(Long appointmentId, Integer score, String comment) {}

    private Patient me(String subject) {
        return patients.findById(PortalService.patientIdFrom(subject)).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    private Appointment mine(Patient p, Long appointmentId) {
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("La cita"));
        if (!a.patient.id.equals(p.id)) throw ApiException.notFound("La cita");
        return a;
    }

    // ------------------------------------------------------------------ reservas

    @Transactional(readOnly = true)
    public Map<String, Object> bookingOptions() {
        List<Map<String, Object>> ds = new ArrayList<>();
        for (Dentist d : agenda.activeDentists()) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.id);
            dm.put("fullName", d.getFullName());
            dm.put("specialty", d.specialty);
            dm.put("color", d.color);
            dm.put("photoUrl", d.getPhotoUrl());
            ds.add(dm);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dentists", ds);
        m.put("reasons", REASONS);
        m.put("rules", Map.of("minHoursToBook", MIN_HOURS_TO_BOOK, "minHoursToChange", MIN_HOURS_TO_CHANGE,
                "maxDaysAhead", MAX_DAYS_AHEAD, "maxOpenAppointments", MAX_OPEN));
        return m;
    }

    /** Horarios libres de un odontólogo para un día (solo los que el paciente puede tomar). */
    @Transactional(readOnly = true)
    public Map<String, Object> slots(String subject, Long dentistId, LocalDate date, String reasonCode, Long excludeId) {
        Patient p = me(subject);
        int duration = durationOf(reasonCode);
        if (date.isBefore(Dates.today()) || date.isAfter(Dates.today().plusDays(MAX_DAYS_AHEAD))) {
            return Map.of("date", date, DURATIONMIN, duration, SLOTS, List.of());
        }
        // Se ocultan también las horas en que el paciente ya tiene cita con otro odontólogo.
        Map<String, Object> raw = agenda.slots(dentistId, date, duration, excludeId, p.id);
        LocalDateTime minStart = Dates.now().plusHours(MIN_HOURS_TO_BOOK);
        List<String> free = new ArrayList<>();
        Object list = raw.get(SLOTS);
        if (list instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof SlotCalculator.Slot s && s.available()) {
                    LocalDateTime start = s.start();
                    if (!start.isBefore(minStart)) free.add(start.toString());
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put(DURATIONMIN, duration);
        m.put("works", raw.get("works"));
        m.put(SLOTS, free);
        return m;
    }

    private static int durationOf(String code) {
        for (Map<String, Object> r : REASONS) if (r.get("code").equals(code)) return (Integer) r.get(DURATIONMIN);
        return 30;
    }

    private static String labelOf(String code) {
        for (Map<String, Object> r : REASONS) if (r.get("code").equals(code)) return (String) r.get(LABEL);
        return null;
    }

    // READ_COMMITTED: la verificación de cruces dentro de AgendaService debe ver las citas recién confirmadas
    // por otras transacciones (con REPEATABLE READ dos reservas simultáneas podrían aceptarse).
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> book(String subject, BookRequest r) {
        Patient p = me(subject);
        String label = labelOf(r.reasonCode());
        if (label == null) throw ApiException.field("reasonCode", "Elige el motivo de tu cita.");
        if (r.startAt() == null) throw ApiException.field(STARTAT, "Elige un horario.");
        if (r.startAt().isBefore(Dates.now().plusHours(MIN_HOURS_TO_BOOK))) {
            throw ApiException.field(STARTAT, "Las citas se reservan con al menos " + MIN_HOURS_TO_BOOK + " horas de anticipación.");
        }
        if (r.startAt().toLocalDate().isAfter(Dates.today().plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.field(STARTAT, "Puedes reservar hasta " + MAX_DAYS_AHEAD + " días antes.");
        }
        long open = appointments.findByPatientIdOrderByStartAtDesc(p.id).stream()
                .filter(a -> !a.status.isFinal() && a.endAt.isAfter(Dates.now())).count();
        if (open >= MAX_OPEN) {
            throw ApiException.conflict("LIMITE_CITAS", "Ya tienes " + open + " citas pendientes. Para más, comunícate con la clínica.");
        }
        String notes = "Reservada por el paciente desde la app" + (r.notes() == null || r.notes().isBlank() ? "" : ": " + r.notes().trim());
        Appointment a = agenda.create(new AgendaService.AppointmentRequest(p.id, r.dentistId(), r.startAt(),
                durationOf(r.reasonCode()), label, notes.length() > 480 ? notes.substring(0, 480) : notes, null));
        a.bookedVia = "APP";
        appointments.save(a);
        audit.register("CITA", a.id, "RESERVA_APP", "Reservada por " + p.getFullName() + " desde la app");
        return Map.of("id", a.id, STARTAT, a.startAt, MESSAGE, "¡Listo! Tu cita quedó reservada.");
    }

    private void checkChangeable(Appointment a) {
        if (a.status.isFinal() || a.status == AppointmentStatus.EN_ESPERA || a.status == AppointmentStatus.EN_ATENCION) {
            throw ApiException.conflict("CITA_CERRADA", "Esta cita ya no se puede modificar.");
        }
        if (a.startAt.isBefore(Dates.now().plusHours(MIN_HOURS_TO_CHANGE))) {
            throw ApiException.conflict("FUERA_DE_PLAZO", "Solo puedes cambiar o cancelar tu cita hasta " + MIN_HOURS_TO_CHANGE
                    + " horas antes. Llama a la clínica para ayudarte.");
        }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> reschedule(String subject, Long id, RescheduleRequest r) {
        Patient p = me(subject);
        Appointment a = mine(p, id);
        checkChangeable(a);
        if (r.startAt() == null) throw ApiException.field(STARTAT, "Elige el nuevo horario.");
        if (r.startAt().isBefore(Dates.now().plusHours(MIN_HOURS_TO_BOOK))) {
            throw ApiException.field(STARTAT, "Elige un horario con al menos " + MIN_HOURS_TO_BOOK + " horas de anticipación.");
        }
        Appointment updated = agenda.reschedule(a.id, new AgendaService.AppointmentRequest(p.id,
                r.dentistId() == null ? a.dentist.id : r.dentistId(), r.startAt(), a.durationMin, a.reason, a.notes,
                "Reprogramada por el paciente desde la app"));
        return Map.of("id", updated.id, STARTAT, updated.startAt, MESSAGE, "Tu cita fue reprogramada.");
    }

    @Transactional
    public Map<String, Object> cancel(String subject, Long id, CancelRequest r) {
        Patient p = me(subject);
        Appointment a = mine(p, id);
        checkChangeable(a);
        String reason = r == null || r.reason() == null || r.reason().isBlank() ? "Cancelada por el paciente desde la app"
                : "Paciente (app): " + r.reason().trim();
        agenda.changeStatus(a.id, new AgendaService.StatusRequest(AppointmentStatus.CANCELADA, reason));
        return Map.of(MESSAGE, "Tu cita fue cancelada.");
    }

    // ------------------------------------------------------------------ avisos (push simulado)

    @Transactional(readOnly = true)
    public Map<String, Object> notices(String subject) {
        Patient p = me(subject);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", notices.countByPatientIdAndReadAtIsNull(p.id));
        m.put("items", notices.findTop50ByPatientIdOrderByIdDesc(p.id));
        return m;
    }

    @Transactional
    public Map<String, Object> markRead(String subject) {
        Patient p = me(subject);
        notices.markAllRead(p.id, Dates.now());
        return Map.of("unread", 0);
    }

    // ------------------------------------------------------------------ pagos en línea

    /** Sin transacción propia: el cobro y la boleta van en transacciones separadas (si la boleta falla, el pago queda). */
    public Map<String, Object> pay(String subject, PayRequest r) {
        Patient p = me(subject);
        BigDecimal amount = r.amount() == null ? null : r.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount == null || amount.signum() <= 0) throw ApiException.field(AMOUNT, "Ingresa el monto a pagar.");
        Object bal = cash.balance(p.id).get("balance");
        BigDecimal balance = bal instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(bal));
        if (balance.signum() <= 0) throw ApiException.conflict("SIN_DEUDA", "No tienes saldo pendiente.");
        if (amount.compareTo(balance) > 0) throw ApiException.field(AMOUNT, "El monto supera tu saldo pendiente (S/ " + balance.toPlainString() + ").");
        String method = r.method() == null ? "" : r.method().trim().toUpperCase();
        SimulatedPaymentGateway.Result res = gateway.charge(new SimulatedPaymentGateway.Charge(method, r.approvalCode(),
                r.cardNumber(), r.cardExpiry(), r.cardCvv()));
        Map<String, Object> paid = cash.registerPayment(new CashService.PaymentRequest(p.id, null, amount, method,
                res.operationId(), "Pago en línea desde la app · " + res.reference()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operationId", res.operationId());
        out.put(AMOUNT, amount);
        out.put("payment", paid.get("payment"));
        // Boleta electrónica automática (si falla, el pago igual queda registrado y caja puede emitirla)
        try {
            String cdt = switch (p.docType) { case "DNI" -> "1"; case "CE" -> "4"; case "PAS" -> "7"; default -> "0"; };
            Map<String, Object> inv = billing.create(new BillingService.InvoiceRequest("03", p.id, cdt, p.docNumber,
                    p.getFullName(), null, List.of(new BillingService.LineRequest("Pago de tratamiento dental (app)",
                    BigDecimal.ONE, amount, null))));
            if (inv.get("invoice") instanceof Invoice created) {
                sendToSunat(created.id);
                out.put("invoiceId", created.id);
                out.put("invoice", created.getFullNumber());
            }
        } catch (Exception e) {
            log.warn("No se pudo emitir la boleta del pago en línea: {}", e.getMessage());
        }
        out.put(MESSAGE, "Pago aprobado. ¡Gracias!");
        return out;
    }

    // ------------------------------------------------------------------ documentos

    @Transactional(readOnly = true)
    public Map<String, Object> documents(String subject) {
        Patient p = me(subject);
        List<Map<String, Object>> invoices = new ArrayList<>();
        for (Invoice i : billing.byPatient(p.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id);
            m.put("number", i.getFullNumber());
            m.put("type", i.getDocTypeLabel());
            m.put("date", i.issueDate);
            m.put("total", i.totalAmount);
            m.put("status", i.status);
            invoices.add(m);
        }
        List<Map<String, Object>> docs = new ArrayList<>();
        for (PatientFile f : files.shared(p.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id);
            m.put("kind", f.kind);
            m.put("title", f.title);
            m.put("date", f.createdAt);
            m.put("contentType", f.contentType);
            docs.add(m);
        }
        return Map.of("invoices", invoices, "files", docs);
    }

    @Transactional(readOnly = true)
    public Map<String, String> fileLink(String subject, Long fileId) {
        Patient p = me(subject);
        PatientFile f = files.get(fileId);
        if (!f.patientId.equals(p.id) || !f.sharedWithPatient) throw ApiException.notFound("El documento");
        return Map.of("url", files.link(f.id));
    }

    @Transactional(readOnly = true)
    public Map<String, String> invoiceLink(String subject, Long invoiceId) {
        Patient p = me(subject);
        Invoice i = billing.get(invoiceId);
        if (!p.id.equals(i.patientId)) throw ApiException.notFound("El comprobante");
        return Map.of("url", files.signedPath("/api/public/invoices/", i.id));
    }

    /** Representación impresa (PDF) de una boleta o factura electrónica, con formato SUNAT. */
    @Transactional(readOnly = true)
    public byte[] invoicePdf(Long invoiceId) {
        Invoice i = billing.get(invoiceId);
        return pe.clinicacardenas.billing.InvoicePdf.render(i, invoiceItems.findByInvoiceIdOrderByLineNumberAsc(i.id), billing.company(),
                pe.clinicacardenas.billing.AmountInWords.of(i.totalAmount), clinicPhone);
    }

    @org.springframework.beans.factory.annotation.Value("${app.notifications.clinic-phone:987 654 000}")
    private String clinicPhone;

    // ------------------------------------------------------------------ encuesta

    @Transactional(readOnly = true)
    public Map<String, Object> pendingSurvey(String subject) {
        Patient p = me(subject);
        LocalDateTime since = Dates.now().minusDays(14);
        for (Appointment a : appointments.findByPatientIdOrderByStartAtDesc(p.id)) {
            if (a.startAt.isBefore(since)) break;
            if (a.status == AppointmentStatus.ATENDIDA && !surveys.existsByAppointmentId(a.id)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("appointmentId", a.id);
                m.put(STARTAT, a.startAt);
                m.put("reason", a.reason);
                m.put("dentist", a.dentist.getFullName());
                return Map.of("pending", m);
            }
        }
        return Collections.singletonMap("pending", null);
    }

    @Transactional
    public Map<String, Object> rate(String subject, SurveyRequest r) {
        Patient p = me(subject);
        if (r.appointmentId() == null) throw ApiException.field("appointmentId", "Falta la cita.");
        Appointment a = mine(p, r.appointmentId());
        if (a.status != AppointmentStatus.ATENDIDA) throw ApiException.conflict("SIN_ATENCION", "Solo se califican citas atendidas.");
        if (surveys.existsByAppointmentId(a.id)) throw ApiException.conflict("YA_CALIFICADA", "Ya calificaste esta cita. ¡Gracias!");
        if (r.score() == null || r.score() < 1 || r.score() > 5) throw ApiException.field("score", "Elige de 1 a 5 estrellas.");
        Survey s = new Survey();
        s.appointmentId = a.id;
        s.patientId = p.id;
        s.dentistId = a.dentist.id;
        s.score = r.score();
        s.comment = r.comment() == null || r.comment().isBlank() ? null : r.comment().trim().substring(0, Math.min(500, r.comment().trim().length()));
        s.createdAt = Dates.now();
        surveys.save(s);
        return Map.of(MESSAGE, "¡Gracias por tu calificación!");
    }

    private void sendToSunat(Long invoiceId) {
        try { billing.send(invoiceId); } catch (Exception e) { log.warn("Boleta del pago en línea no enviada a SUNAT: {}", e.getMessage()); }
    }
}
