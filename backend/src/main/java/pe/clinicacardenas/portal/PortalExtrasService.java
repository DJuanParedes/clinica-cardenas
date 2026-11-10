package pe.clinicacardenas.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.billing.BillingService;
import pe.clinicacardenas.billing.Invoice;
import pe.clinicacardenas.billing.InvoiceItem;
import pe.clinicacardenas.billing.InvoiceItemRepository;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.files.SimplePdf;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Funciones de la app del paciente: reservar, reprogramar y cancelar citas; avisos; pagos en línea (pasarela
 * simulada) con boleta electrónica; documentos compartidos y encuesta de satisfacción.
 */
@Service
public class PortalExtrasService {

    private static final Logger log = LoggerFactory.getLogger(PortalExtrasService.class);

    /** Motivos que el paciente puede elegir al reservar, con su duración. */
    public static final List<Map<String, Object>> REASONS = List.of(
            Map.of("code", "EVALUACION", "label", "Evaluación general", "durationMin", 30),
            Map.of("code", "LIMPIEZA", "label", "Limpieza dental", "durationMin", 45),
            Map.of("code", "DOLOR", "label", "Dolor o molestia", "durationMin", 30),
            Map.of("code", "ORTODONCIA", "label", "Control de ortodoncia", "durationMin", 30),
            Map.of("code", "BLANQUEAMIENTO", "label", "Blanqueamiento", "durationMin", 60),
            Map.of("code", "CONTROL", "label", "Control de tratamiento", "durationMin", 30));

    static final int MIN_HOURS_TO_BOOK = 2, MIN_HOURS_TO_CHANGE = 12, MAX_DAYS_AHEAD = 60, MAX_OPEN = 2;

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
    private final String clinicName, clinicAddress;

    public PortalExtrasService(PatientRepository patients, AppointmentRepository appointments, AgendaService agenda,
                               PatientNoticeRepository notices, SurveyRepository surveys, SimulatedPaymentGateway gateway,
                               CashService cash, BillingService billing, InvoiceItemRepository invoiceItems,
                               FileService files, AuditService audit,
                               @Value("${app.company.trade-name}") String clinicName,
                               @Value("${app.company.address}") String clinicAddress) {
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
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
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
            ds.add(Map.of("id", d.id, "fullName", d.getFullName(), "specialty", d.specialty, "color", d.color));
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
    public Map<String, Object> slots(Long dentistId, LocalDate date, String reasonCode, Long excludeId) {
        int duration = durationOf(reasonCode);
        if (date.isBefore(Dates.today()) || date.isAfter(Dates.today().plusDays(MAX_DAYS_AHEAD))) {
            return Map.of("date", date, "durationMin", duration, "slots", List.of());
        }
        Map<String, Object> raw = agenda.slots(dentistId, date, duration, excludeId);
        LocalDateTime minStart = Dates.now().plusHours(MIN_HOURS_TO_BOOK);
        List<String> free = new ArrayList<>();
        Object list = raw.get("slots");
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
        m.put("durationMin", duration);
        m.put("works", raw.get("works"));
        m.put("slots", free);
        return m;
    }

    private static int durationOf(String code) {
        for (Map<String, Object> r : REASONS) if (r.get("code").equals(code)) return (Integer) r.get("durationMin");
        return 30;
    }

    private static String labelOf(String code) {
        for (Map<String, Object> r : REASONS) if (r.get("code").equals(code)) return (String) r.get("label");
        return null;
    }

    @Transactional
    public Map<String, Object> book(String subject, BookRequest r) {
        Patient p = me(subject);
        String label = labelOf(r.reasonCode());
        if (label == null) throw ApiException.field("reasonCode", "Elige el motivo de tu cita.");
        if (r.startAt() == null) throw ApiException.field("startAt", "Elige un horario.");
        if (r.startAt().isBefore(Dates.now().plusHours(MIN_HOURS_TO_BOOK))) {
            throw ApiException.field("startAt", "Las citas se reservan con al menos " + MIN_HOURS_TO_BOOK + " horas de anticipación.");
        }
        if (r.startAt().toLocalDate().isAfter(Dates.today().plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.field("startAt", "Puedes reservar hasta " + MAX_DAYS_AHEAD + " días antes.");
        }
        long open = appointments.findByPatientIdOrderByStartAtDesc(p.id).stream()
                .filter(a -> !a.status.isFinal() && a.endAt.isAfter(Dates.now())).count();
        if (open >= MAX_OPEN) {
            throw ApiException.conflict("LIMITE_CITAS", "Ya tienes " + open + " citas pendientes. Para más, comunícate con la clínica.");
        }
        String notes = "Reservada por el paciente desde la app" + (r.notes() == null || r.notes().isBlank() ? "" : ": " + r.notes().trim());
        Appointment a = agenda.create(new AgendaService.AppointmentRequest(p.id, r.dentistId(), r.startAt(),
                durationOf(r.reasonCode()), label, notes.length() > 480 ? notes.substring(0, 480) : notes, null));
        audit.record("CITA", a.id, "RESERVA_APP", "Reservada por " + p.getFullName() + " desde la app");
        return Map.of("id", a.id, "startAt", a.startAt, "message", "¡Listo! Tu cita quedó reservada.");
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

    @Transactional
    public Map<String, Object> reschedule(String subject, Long id, RescheduleRequest r) {
        Patient p = me(subject);
        Appointment a = mine(p, id);
        checkChangeable(a);
        if (r.startAt() == null) throw ApiException.field("startAt", "Elige el nuevo horario.");
        if (r.startAt().isBefore(Dates.now().plusHours(MIN_HOURS_TO_BOOK))) {
            throw ApiException.field("startAt", "Elige un horario con al menos " + MIN_HOURS_TO_BOOK + " horas de anticipación.");
        }
        Appointment updated = agenda.reschedule(a.id, new AgendaService.AppointmentRequest(p.id,
                r.dentistId() == null ? a.dentist.id : r.dentistId(), r.startAt(), a.durationMin, a.reason, a.notes,
                "Reprogramada por el paciente desde la app"));
        return Map.of("id", updated.id, "startAt", updated.startAt, "message", "Tu cita fue reprogramada.");
    }

    @Transactional
    public Map<String, Object> cancel(String subject, Long id, CancelRequest r) {
        Patient p = me(subject);
        Appointment a = mine(p, id);
        checkChangeable(a);
        String reason = r == null || r.reason() == null || r.reason().isBlank() ? "Cancelada por el paciente desde la app"
                : "Paciente (app): " + r.reason().trim();
        agenda.changeStatus(a.id, new AgendaService.StatusRequest(AppointmentStatus.CANCELADA, reason));
        return Map.of("message", "Tu cita fue cancelada.");
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
        if (amount == null || amount.signum() <= 0) throw ApiException.field("amount", "Ingresa el monto a pagar.");
        Object bal = cash.balance(p.id).get("balance");
        BigDecimal balance = bal instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(bal));
        if (balance.signum() <= 0) throw ApiException.conflict("SIN_DEUDA", "No tienes saldo pendiente.");
        if (amount.compareTo(balance) > 0) throw ApiException.field("amount", "El monto supera tu saldo pendiente (S/ " + balance.toPlainString() + ").");
        String method = r.method() == null ? "" : r.method().trim().toUpperCase();
        SimulatedPaymentGateway.Result res = gateway.charge(new SimulatedPaymentGateway.Charge(method, r.approvalCode(),
                r.cardNumber(), r.cardExpiry(), r.cardCvv()), amount);
        Map<String, Object> paid = cash.registerPayment(new CashService.PaymentRequest(p.id, null, amount, method,
                res.operationId(), "Pago en línea desde la app · " + res.reference()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operationId", res.operationId());
        out.put("amount", amount);
        out.put("payment", paid.get("payment"));
        // Boleta electrónica automática (si falla, el pago igual queda registrado y caja puede emitirla)
        try {
            String cdt = switch (p.docType) { case "DNI" -> "1"; case "CE" -> "4"; case "PAS" -> "7"; default -> "0"; };
            Map<String, Object> inv = billing.create(new BillingService.InvoiceRequest("03", p.id, cdt, p.docNumber,
                    p.getFullName(), null, List.of(new BillingService.LineRequest("Pago de tratamiento dental (app)",
                    BigDecimal.ONE, amount, null))));
            if (inv.get("invoice") instanceof Invoice created) {
                try { billing.send(created.id); } catch (Exception e) { log.warn("Boleta del pago en línea no enviada a SUNAT: {}", e.getMessage()); }
                out.put("invoiceId", created.id);
                out.put("invoice", created.getFullNumber());
            }
        } catch (Exception e) {
            log.warn("No se pudo emitir la boleta del pago en línea: {}", e.getMessage());
        }
        out.put("message", "Pago aprobado. ¡Gracias!");
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

    /** Representación impresa (PDF) de una boleta o factura electrónica. */
    @Transactional(readOnly = true)
    public byte[] invoicePdf(Long invoiceId) {
        Invoice i = billing.get(invoiceId);
        SimplePdf pdf = new SimplePdf()
                .header(clinicName, clinicAddress + " · RUC 10102984841")
                .title(i.getDocTypeLabel() + "  " + i.getFullNumber())
                .paragraph("Fecha de emisión: " + i.issueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "  " + i.issueTime, 10, false)
                .paragraph("Cliente: " + i.customerName + "  ·  Documento: " + i.customerDocNumber, 10, false)
                .space(8);
        for (InvoiceItem it : invoiceItems.findByInvoiceIdOrderByLineNumberAsc(i.id)) {
            pdf.paragraph(it.quantity.stripTrailingZeros().toPlainString() + " x " + it.description + "    S/ " + it.lineTotal.toPlainString(), 10, false);
        }
        pdf.space(8)
                .paragraph("Op. gravada: S/ " + i.taxableAmount.toPlainString() + "    IGV (18%): S/ " + i.igvAmount.toPlainString(), 10, false)
                .paragraph("TOTAL: S/ " + i.totalAmount.toPlainString(), 13, true)
                .space(10)
                .paragraph("Estado SUNAT: " + i.status + (i.sunatDescription == null ? "" : " · " + i.sunatDescription), 9, false)
                .paragraph("Representación impresa del comprobante electrónico. Hash: " + (i.hash == null ? "-" : i.hash), 8, false);
        return pdf.build();
    }

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
                m.put("startAt", a.startAt);
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
        return Map.of("message", "¡Gracias por tu calificación!");
    }
}
