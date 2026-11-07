package pe.clinicacardenas.care;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.clinical.TreatmentPlan;
import pe.clinicacardenas.clinical.TreatmentPlanRepository;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.portal.NoticeService;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Seguimiento del paciente: lista de espera, pagos en cuotas, recordatorios de control, satisfacción y
 * respuestas por WhatsApp ("1" confirma, "2" pide reprogramar).
 */
@Service
public class CareService {

    private static final Logger log = LoggerFactory.getLogger(CareService.class);
    private static final Locale ES = new Locale("es", "PE");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' h:mm a", ES);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);

    private final WaitlistRepository waitlist;
    private final InstallmentRepository installments;
    private final PatientRepository patients;
    private final DentistRepository dentists;
    private final AppointmentRepository appointments;
    private final TreatmentPlanRepository plans;
    private final AgendaService agenda;
    private final NotificationService notifications;
    private final NoticeService notices;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public CareService(WaitlistRepository waitlist, InstallmentRepository installments, PatientRepository patients,
                       DentistRepository dentists, AppointmentRepository appointments, TreatmentPlanRepository plans,
                       AgendaService agenda, NotificationService notifications, NoticeService notices,
                       AuditService audit, JdbcTemplate jdbc) {
        this.waitlist = waitlist;
        this.installments = installments;
        this.patients = patients;
        this.dentists = dentists;
        this.appointments = appointments;
        this.plans = plans;
        this.agenda = agenda;
        this.notifications = notifications;
        this.notices = notices;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public record WaitRequest(Long patientId, Long dentistId, String reason, String preference) {}

    public record InstallmentRequest(Integer count, LocalDate firstDueDate) {}

    public record ReplyRequest(String telefono, String texto) {}

    private Patient patient(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    // ------------------------------------------------------------------ lista de espera

    @Transactional(readOnly = true)
    public List<Map<String, Object>> waitlist() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WaitlistEntry w : waitlist.findByStatusInOrderByCreatedAtAsc(List.of("ACTIVA", "AVISADA"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entry", w);
            patients.findById(w.patientId).ifPresent(p -> m.put("patient", Map.of("id", p.id, "fullName", p.getFullName(),
                    "phone", p.phone == null ? "" : p.phone)));
            if (w.dentistId != null) dentists.findById(w.dentistId).ifPresent(d -> m.put("dentist", d.getFullName()));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public WaitlistEntry addToWaitlist(WaitRequest r) {
        if (r.patientId() == null) throw ApiException.field("patientId", "Selecciona el paciente.");
        Patient p = patient(r.patientId());
        if (r.reason() == null || r.reason().isBlank()) throw ApiException.field("reason", "Indica el motivo.");
        if (r.dentistId() != null) dentists.findById(r.dentistId()).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        WaitlistEntry w = new WaitlistEntry();
        w.patientId = p.id;
        w.dentistId = r.dentistId();
        w.reason = r.reason().trim();
        w.preference = r.preference() == null || r.preference().isBlank() ? null : r.preference().trim();
        w.createdAt = Dates.now();
        w.createdBy = CurrentUser.usernameOrSystem();
        waitlist.save(w);
        audit.record("PACIENTE", p.id, "LISTA_ESPERA", "Agregado a la lista de espera: " + w.reason);
        return w;
    }

    @Transactional
    public WaitlistEntry closeWaitlist(Long id) {
        WaitlistEntry w = waitlist.findById(id).orElseThrow(() -> ApiException.notFound("La solicitud"));
        w.status = "CERRADA";
        w.closedAt = Dates.now();
        return waitlist.save(w);
    }

    /** Si se cancela una cita futura, se avisa a los primeros de la lista de espera (del mismo odontólogo o de cualquiera). */
    @EventListener
    public void onAppointment(AppointmentEvent e) {
        Appointment a = e.appointment();
        if (e.type() != AppointmentEvent.Type.STATUS || a.status != AppointmentStatus.CANCELADA) return;
        if (!a.startAt.isAfter(Dates.now().plusHours(2))) return;
        int sent = 0;
        for (WaitlistEntry w : waitlist.findByStatusInOrderByCreatedAtAsc(List.of("ACTIVA"))) {
            if (sent >= 3) break;
            if (w.dentistId != null && !w.dentistId.equals(a.dentist.id)) continue;
            if (w.patientId.equals(a.patient.id)) continue;
            Patient p = patients.findById(w.patientId).orElse(null);
            if (p == null) continue;
            String when = a.startAt.format(WHEN);
            notifications.message(p, "LISTA_ESPERA", "Se liberó un espacio",
                    "se liberó un espacio el " + when + " con " + a.dentist.getFullName()
                            + ". Si lo quieres, escríbenos o llámanos pronto para reservarlo.",
                    "espera:" + w.id + ":" + a.id);
            notices.add(p.id, "LISTA_ESPERA", "Se liberó un espacio", "Hay un horario libre el " + when
                    + " con " + a.dentist.getFullName() + ". Resérvalo desde la app o llamando a la clínica.", a.id);
            w.status = "AVISADA";
            w.notifiedAt = Dates.now();
            waitlist.save(w);
            sent++;
        }
    }

    // ------------------------------------------------------------------ pagos en cuotas

    private TreatmentPlan plan(Long id) {
        return plans.findById(id).orElseThrow(() -> ApiException.notFound("El plan de tratamiento"));
    }

    private BigDecimal planTotal(Long planId) {
        BigDecimal t = jdbc.queryForObject("select coalesce(sum(price), 0) from treatment_items where plan_id = ? and status <> 'ANULADO'",
                BigDecimal.class, planId);
        return t == null ? BigDecimal.ZERO : t;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> installments(Long planId) {
        TreatmentPlan p = plan(planId);
        List<Installment> list = installments.findByPlanIdOrderByNumberAsc(planId);
        BigDecimal paid = jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from payments
                 where patient_id = ? and status = 'VALIDO' and paid_at >= ?
                """, BigDecimal.class, p.patientId, p.createdAt);
        if (paid == null) paid = BigDecimal.ZERO;
        BigDecimal cumulative = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate today = Dates.today();
        for (Installment i : list) {
            cumulative = cumulative.add(i.amount);
            String status = paid.compareTo(cumulative) >= 0 ? "PAGADA" : i.dueDate.isBefore(today) ? "VENCIDA" : "PENDIENTE";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id);
            m.put("number", i.number);
            m.put("dueDate", i.dueDate);
            m.put("amount", i.amount);
            m.put("status", status);
            rows.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", planId);
        m.put("total", planTotal(planId));
        m.put("paid", paid);
        m.put("items", rows);
        return m;
    }

    @Transactional
    public Map<String, Object> createInstallments(Long planId, InstallmentRequest r) {
        TreatmentPlan p = plan(planId);
        if ("ANULADO".equals(p.status)) throw ApiException.badRequest("PLAN_CERRADO", "El plan está anulado.");
        int count = r.count() == null ? 0 : r.count();
        if (count < 2 || count > 24) throw ApiException.field("count", "Elige de 2 a 24 cuotas.");
        LocalDate first = r.firstDueDate() == null ? Dates.today().plusMonths(1) : r.firstDueDate();
        if (first.isBefore(Dates.today())) throw ApiException.field("firstDueDate", "La primera cuota no puede ser en el pasado.");
        BigDecimal total = planTotal(planId);
        if (total.signum() <= 0) throw ApiException.badRequest("PLAN_VACIO", "El plan no tiene procedimientos con precio.");
        installments.deleteByPlanId(planId);
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal last = total.subtract(each.multiply(BigDecimal.valueOf(count - 1)));
        for (int n = 1; n <= count; n++) {
            Installment i = new Installment();
            i.planId = planId;
            i.number = n;
            i.dueDate = first.plusMonths(n - 1);
            i.amount = n == count ? last : each;
            i.createdAt = Dates.now();
            i.createdBy = CurrentUser.usernameOrSystem();
            installments.save(i);
        }
        audit.record("HISTORIA", p.patientId, "CUOTAS", count + " cuotas para \"" + p.title + "\" (S/ " + total.toPlainString() + ")");
        return installments(planId);
    }

    /** Todos los días a las 9:00 a. m.: recordatorio de las cuotas que vencen en 3 días. */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Lima")
    @Transactional
    public void remindInstallments() {
        for (Installment i : installments.findByDueDateAndRemindedAtIsNull(Dates.today().plusDays(3))) {
            plans.findById(i.planId).flatMap(p -> patients.findById(p.patientId)).ifPresent(p -> {
                String text = "te recordamos que tu cuota " + i.number + " de S/ " + i.amount.toPlainString() + " vence el "
                        + i.dueDate.format(DAY) + ". Puedes pagarla en la app o en recepción.";
                notifications.message(p, "CUOTA", "Recordatorio de cuota", text, "cuota:" + i.id);
                notices.add(p.id, "CUOTA", "Tu cuota vence pronto", "Cuota " + i.number + " de S/ " + i.amount.toPlainString()
                        + " vence el " + i.dueDate.format(DAY) + ".", i.planId);
            });
            i.remindedAt = Dates.now();
            installments.save(i);
        }
    }

    // ------------------------------------------------------------------ recordatorios de control

    /**
     * Pacientes por volver: limpieza hace más de 6 meses, o sin visitas en 6 meses, o con ortodoncia activa sin
     * control en 35 días; y sin citas pendientes.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recalls() {
        return jdbc.queryForList("""
                select p.id as patientId, concat(p.first_name, ' ', p.last_name) as fullName, p.phone, p.email, p.demo,
                       v.last_visit as lastVisit, c.last_cleaning as lastCleaning, o.ortho_plans as orthoPlans,
                       r.last_recall as lastRecallAt,
                       case when o.ortho_plans > 0 and (v.last_visit is null or v.last_visit < now() - interval 35 day) then 'ORTODONCIA'
                            when c.last_cleaning is null or c.last_cleaning < now() - interval 6 month then 'LIMPIEZA'
                            else 'CONTROL' end as kind
                  from patients p
                  left join (select patient_id, max(start_at) last_visit from appointments where status = 'ATENDIDA' group by patient_id) v
                         on v.patient_id = p.id
                  left join (select tp.patient_id, max(ti.done_at) last_cleaning from treatment_items ti
                               join treatment_plans tp on tp.id = ti.plan_id join procedures pr on pr.id = ti.procedure_id
                              where pr.code = 'PRE-001' and ti.status = 'REALIZADO' group by tp.patient_id) c on c.patient_id = p.id
                  left join (select tp.patient_id, count(*) ortho_plans from treatment_items ti
                               join treatment_plans tp on tp.id = ti.plan_id join procedures pr on pr.id = ti.procedure_id
                              where pr.code = 'ORT-001' and ti.status in ('PENDIENTE', 'EN_PROCESO') and tp.status = 'ACTIVO'
                              group by tp.patient_id) o on o.patient_id = p.id
                  left join (select patient_id, max(sent_at) last_recall from recall_log group by patient_id) r on r.patient_id = p.id
                 where p.active = true
                   and v.last_visit is not null
                   and not exists (select 1 from appointments a where a.patient_id = p.id
                                     and a.status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA') and a.start_at > now())
                   and ( (o.ortho_plans > 0 and v.last_visit < now() - interval 35 day)
                      or v.last_visit < now() - interval 6 month
                      or (c.last_cleaning is not null and c.last_cleaning < now() - interval 6 month) )
                 order by v.last_visit asc
                 limit 200
                """);
    }

    private static String recallText(String kind) {
        return switch (kind) {
            case "ORTODONCIA" -> "ya te toca tu control de ortodoncia. Reserva tu cita desde la app o escríbenos para coordinar.";
            case "LIMPIEZA" -> "ya pasaron más de 6 meses desde tu última limpieza dental. Te recomendamos una profilaxis para mantener tu sonrisa sana. Reserva desde la app o escríbenos.";
            default -> "hace tiempo que no te vemos. Te recomendamos un control dental preventivo. Reserva desde la app o escríbenos.";
        };
    }

    @Transactional
    public Map<String, Object> sendRecall(Long patientId, String kind) {
        Patient p = patient(patientId);
        String k = kind == null || kind.isBlank() ? "CONTROL" : kind.trim().toUpperCase();
        int queued = notifications.message(p, "CONTROL", "Te toca tu control dental", recallText(k),
                "control:" + p.id + ":" + k + ":" + Dates.today().withDayOfMonth(1));
        notices.add(p.id, "CONTROL", "Te toca tu control", recallText(k).substring(0, 1).toUpperCase() + recallText(k).substring(1), null);
        jdbc.update("insert into recall_log (patient_id, kind, sent_at, sent_by) values (?, ?, ?, ?)",
                p.id, k, Dates.now(), CurrentUser.usernameOrSystem());
        audit.record("PACIENTE", p.id, "RECORDATORIO_CONTROL", k.toLowerCase());
        return Map.of("queued", queued, "message", queued > 0 ? "Recordatorio enviado." : "Ya se le envió este recordatorio este mes.");
    }

    /** Todos los lunes a las 10:00 a. m. se envían solos los recordatorios de control (máximo uno al mes por paciente). */
    @Scheduled(cron = "0 0 10 * * MON", zone = "America/Lima")
    public void autoRecalls() {
        int n = 0;
        for (Map<String, Object> r : recalls()) {
            Object last = r.get("lastRecallAt");
            if (last instanceof java.sql.Timestamp t && t.toLocalDateTime().isAfter(Dates.now().minusDays(30))) continue;
            if (last instanceof LocalDateTime t && t.isAfter(Dates.now().minusDays(30))) continue;
            try {
                sendRecall(((Number) r.get("patientId")).longValue(), String.valueOf(r.get("kind")));
                n++;
            } catch (Exception e) {
                log.warn("Recordatorio de control no enviado: {}", e.getMessage());
            }
        }
        if (n > 0) log.info("Recordatorios de control enviados: {}", n);
    }

    // ------------------------------------------------------------------ satisfacción

    @Transactional(readOnly = true)
    public Map<String, Object> satisfaction(int days) {
        LocalDateTime since = Dates.now().minusDays(Math.max(1, Math.min(days, 365)));
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> agg = jdbc.queryForMap("select count(*) as total, coalesce(avg(score), 0) as average from surveys where created_at >= ?", since);
        m.put("days", days);
        m.put("total", agg.get("total"));
        m.put("average", new BigDecimal(String.valueOf(agg.get("average"))).setScale(2, RoundingMode.HALF_UP));
        m.put("distribution", jdbc.queryForList("select score, count(*) as count from surveys where created_at >= ? group by score order by score desc", since));
        m.put("byDentist", jdbc.queryForList("""
                select d.id as dentistId, concat(u.full_name) as dentist, count(*) as total, round(avg(s.score), 2) as average
                  from surveys s join dentists d on d.id = s.dentist_id join users u on u.id = d.user_id
                 where s.created_at >= ? group by d.id, u.full_name order by average desc
                """, since));
        m.put("comments", jdbc.queryForList("""
                select s.score, s.comment, s.created_at as createdAt, concat(p.first_name, ' ', p.last_name) as patient
                  from surveys s join patients p on p.id = s.patient_id
                 where s.created_at >= ? and s.comment is not null order by s.created_at desc limit 20
                """, since));
        return m;
    }

    // ------------------------------------------------------------------ respuestas por WhatsApp

    /**
     * El microservicio de notificaciones reenvía lo que el paciente escribe por WhatsApp. "1" confirma la próxima
     * cita; "2" marca que quiere reprogramar (recepción lo ve en la agenda). Devuelve el texto de respuesta.
     */
    @Transactional
    public Map<String, Object> whatsappReply(ReplyRequest r) {
        String phone = r.telefono() == null ? "" : r.telefono().replaceAll("\\D", "");
        if (phone.startsWith("51") && phone.length() == 11) phone = phone.substring(2);
        String text = r.texto() == null ? "" : r.texto().trim().toLowerCase();
        String option = text.matches("^1\\b.*|^s[ií]\\b.*|^confirmo.*") ? "1" : text.matches("^2\\b.*|^reprogram.*") ? "2" : null;
        Map<String, Object> out = new LinkedHashMap<>();
        if (phone.length() != 9) return Map.of("accion", "IGNORADO");
        List<Long> ids = jdbc.queryForList("select id from patients where replace(replace(phone, ' ', ''), '-', '') = ? and active = true",
                Long.class, phone);
        if (ids.isEmpty()) return Map.of("accion", "DESCONOCIDO");
        Appointment next = null;
        for (Long id : ids) {
            for (Appointment a : appointments.findByPatientIdOrderByStartAtDesc(id)) {
                if ((a.status == AppointmentStatus.PROGRAMADA || a.status == AppointmentStatus.CONFIRMADA)
                        && a.startAt.isAfter(Dates.now()) && a.startAt.isBefore(Dates.now().plusDays(8))
                        && (next == null || a.startAt.isBefore(next.startAt))) next = a;
            }
        }
        if (next == null) {
            out.put("accion", "SIN_CITA");
            if (option != null) out.put("respuesta", "No encontramos una cita próxima a tu nombre. Si necesitas ayuda, llámanos o escríbenos aquí mismo.");
            return out;
        }
        String when = next.startAt.format(WHEN);
        if ("1".equals(option)) {
            if (next.status == AppointmentStatus.PROGRAMADA) {
                agenda.changeStatus(next.id, new AgendaService.StatusRequest(AppointmentStatus.CONFIRMADA, "Confirmada por WhatsApp"));
            }
            out.put("accion", "CONFIRMADA");
            out.put("citaId", next.id);
            out.put("respuesta", "¡Gracias! Tu cita del " + when + " quedó confirmada. Te esperamos 😊");
        } else if ("2".equals(option)) {
            next.rescheduleRequestedAt = Dates.now();
            appointments.save(next);
            audit.record("CITA", next.id, "SOLICITA_REPROGRAMAR", "El paciente respondió 2 por WhatsApp");
            notices.add(next.patient.id, "CITA_REPROGRAMAR", "Recibimos tu pedido",
                    "Recepción te contactará para elegir un nuevo horario. También puedes reprogramar desde la app.", next.id);
            out.put("accion", "REPROGRAMAR");
            out.put("citaId", next.id);
            out.put("respuesta", "Entendido. Recepción te escribirá para elegir un nuevo horario. También puedes reprogramar desde la app de la clínica.");
        } else {
            out.put("accion", "MENSAJE");
            out.put("citaId", next.id);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rescheduleRequests() {
        return jdbc.queryForList("""
                select a.id, a.start_at as startAt, a.reschedule_requested_at as requestedAt, a.reason,
                       concat(p.first_name, ' ', p.last_name) as patient, p.phone, u.full_name as dentist
                  from appointments a join patients p on p.id = a.patient_id join dentists d on d.id = a.dentist_id
                  join users u on u.id = d.user_id
                 where a.reschedule_requested_at is not null and a.status in ('PROGRAMADA', 'CONFIRMADA') and a.start_at > now()
                 order by a.start_at
                """);
    }
}
