package pe.clinicacardenas.portal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.files.SimplePdf;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lo que el paciente puede ver y hacer sobre su propia información (Ley 29414, derecho a acceder a su historia
 * clínica; Ley 29733, derechos ARCO):
 *  - actualizar su celular y correo (nombre, documento y fecha de nacimiento no: vienen de su documento/RENIEC);
 *  - sus planes de tratamiento, lo que se le realizó cada día, lo que falta y cuánto pagó por cada cosa;
 *  - un resumen de su ficha clínica (alergias, antecedentes) y sus atenciones;
 *  - sus consentimientos (constancia en PDF y la copia firmada);
 *  - los archivos que la clínica le pidió (placa, radiografía, informe) y subirlos.
 * También lo usa el personal para pedir archivos y subirlos en nombre del paciente.
 */
@Service
public class PortalRecordsService {

    private static final Pattern PHONE = Pattern.compile("^9\\d{8}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JdbcTemplate jdbc;
    private final PatientRepository patients;
    private final FileService files;
    private final AuditService audit;

    public PortalRecordsService(JdbcTemplate jdbc, PatientRepository patients, FileService files, AuditService audit) {
        this.jdbc = jdbc;
        this.patients = patients;
        this.files = files;
        this.audit = audit;
    }

    private Patient me(String subject) {
        return patients.findById(PortalService.patientIdFrom(subject)).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    // ------------------------------------------------------------------ contacto

    public record ContactRequest(String phone, String email) {}

    @Transactional
    public Map<String, Object> updateContact(String subject, ContactRequest r) {
        Patient p = me(subject);
        Map<String, String> e = new LinkedHashMap<>();
        String phone = r.phone() == null ? "" : r.phone().replaceAll("\\s", "");
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!PHONE.matcher(phone).matches()) e.put("phone", "Celular de 9 dígitos que empiece con 9.");
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) e.put("email", "Correo no válido.");
        if (email.isEmpty() && "EMAIL".equals(p.notifyChannel)) e.put("email", "Recibes avisos por correo: no lo dejes vacío.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa tus datos.", e);
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(p.phone, phone)) changes.add("celular");
        if (!Objects.equals(p.email == null ? "" : p.email, email)) changes.add("correo");
        p.phone = phone;
        p.email = email.isEmpty() ? null : email;
        p.updatedAt = Dates.now();
        p.updatedBy = "paciente (app)";
        patients.save(p);
        if (!changes.isEmpty()) audit.record("PACIENTE", p.id, "EDITAR", "El paciente actualizó su " + String.join(" y ", changes) + " desde la app");
        return Map.of("phone", p.phone, "email", p.email == null ? "" : p.email, "message", "Datos de contacto actualizados.");
    }

    // ------------------------------------------------------------------ tratamientos, ficha y pagos

    @Transactional(readOnly = true)
    public Map<String, Object> treatments(String subject) {
        Patient p = me(subject);
        List<Map<String, Object>> plans = jdbc.queryForList("""
                select tp.id, tp.title, tp.notes, tp.status, tp.created_at as createdAt, concat(u.full_name) as dentist
                  from treatment_plans tp left join dentists d on d.id = tp.dentist_id left join users u on u.id = d.user_id
                 where tp.patient_id = ? and tp.status <> 'ANULADO' order by tp.created_at desc""", p.id);
        BigDecimal totalAll = BigDecimal.ZERO, paidAll = BigDecimal.ZERO;
        for (Map<String, Object> plan : plans) {
            Long planId = ((Number) plan.get("id")).longValue();
            List<Map<String, Object>> items = jdbc.queryForList("""
                    select ti.id, pr.name as `procedure`, ti.tooth, ti.price, ti.list_price as listPrice, ti.status, ti.done_at as doneAt,
                           u.full_name as doneBy,
                           coalesce((select sum(py.amount) from payments py where py.treatment_item_id = ti.id and py.status = 'VALIDO'), 0) as paid
                      from treatment_items ti join procedures pr on pr.id = ti.procedure_id
                      left join dentists d on d.id = ti.done_by_dentist_id left join users u on u.id = d.user_id
                     where ti.plan_id = ? and ti.status <> 'ANULADO' order by ti.sort_order, ti.id""", planId);
            BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO;
            int done = 0;
            for (Map<String, Object> it : items) {
                BigDecimal price = (BigDecimal) it.get("price");
                total = total.add(price);
                paid = paid.add((BigDecimal) it.get("paid"));
                if ("REALIZADO".equals(it.get("status"))) done++;
                it.put("balance", price.subtract((BigDecimal) it.get("paid")).max(BigDecimal.ZERO));
            }
            plan.put("items", items);
            plan.put("total", total);
            plan.put("paid", paid);
            plan.put("balance", total.subtract(paid).max(BigDecimal.ZERO));
            plan.put("doneCount", done);
            plan.put("itemCount", items.size());
            plan.put("sessions", jdbc.queryForList("""
                    select e.session_date as date, e.notes, u.full_name as dentist, pr.name as `procedure`
                      from evolutions e left join dentists d on d.id = e.dentist_id left join users u on u.id = d.user_id
                      left join treatment_items ti on ti.id = e.item_id left join procedures pr on pr.id = ti.procedure_id
                     where e.plan_id = ? order by e.session_date desc, e.id desc""", planId));
            plan.put("installments", jdbc.queryForList(
                    "select number, due_date as dueDate, amount from installments where plan_id = ? order by number", planId));
            totalAll = totalAll.add(total);
            paidAll = paidAll.add(paid);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plans", plans);
        out.put("total", totalAll);
        out.put("paid", paidAll);
        out.put("balance", totalAll.subtract(paidAll).max(BigDecimal.ZERO));
        out.put("payments", jdbc.queryForList("""
                select py.id, py.paid_at as paidAt, py.amount, py.method, py.status, pr.name as `procedure`
                  from payments py left join treatment_items ti on ti.id = py.treatment_item_id left join procedures pr on pr.id = ti.procedure_id
                 where py.patient_id = ? order by py.paid_at desc limit 200""", p.id));
        out.put("visits", jdbc.queryForList("""
                select a.start_at as date, a.reason, a.status, u.full_name as dentist
                  from appointments a join dentists d on d.id = a.dentist_id join users u on u.id = d.user_id
                 where a.patient_id = ? and a.status in ('ATENDIDA','NO_ASISTIO') order by a.start_at desc limit 100""", p.id));
        List<Map<String, Object>> h = jdbc.queryForList("""
                select allergies, current_medication as medication, medical_background as background, blood_type as bloodType, updated_at as updatedAt
                  from clinical_histories where patient_id = ?""", p.id);
        out.put("clinical", h.isEmpty() ? null : h.get(0));
        return out;
    }

    // ------------------------------------------------------------------ consentimientos

    @Transactional(readOnly = true)
    public Map<String, Object> consents(String subject) {
        Patient p = me(subject);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.id, r.doc_code as code, r.doc_version as version, r.channel, r.accepted_by as acceptedBy, r.accepted_at as acceptedAt,
                       r.file_id as fileId, d.title
                  from consent_records r left join legal_documents d on d.code = r.doc_code and d.version = r.doc_version
                 where r.patient_id = ? order by r.accepted_at desc""", p.id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", rows);
        out.put("signedFileId", p.consentFileId);
        return out;
    }

    /** Constancia en PDF de una aceptación: qué documento, versión, cuándo, por qué canal y quién aceptó, con el texto. */
    @Transactional(readOnly = true)
    public byte[] consentPdf(String subject, Long recordId) {
        Patient p = me(subject);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.doc_code as code, r.doc_version as version, r.channel, r.accepted_by as acceptedBy, r.accepted_at as acceptedAt,
                       d.title, d.body
                  from consent_records r left join legal_documents d on d.code = r.doc_code and d.version = r.doc_version
                 where r.id = ? and r.patient_id = ?""", recordId, p.id);
        if (rows.isEmpty()) throw ApiException.notFound("El consentimiento");
        Map<String, Object> r = rows.get(0);
        LocalDateTime at = Dates.toLocalDateTime(r.get("acceptedAt"));
        String canal = switch (String.valueOf(r.get("channel"))) {
            case "APP" -> "app móvil del paciente";
            case "PORTAL" -> "portal web del paciente";
            default -> "presencial, en recepción";
        };
        SimplePdf pdf = new SimplePdf()
                .header("Clínica Dental Cárdenas", "Constancia de consentimiento · Ley N.° 29733")
                .title(String.valueOf(r.get("title") == null ? r.get("code") : r.get("title")))
                .paragraph("Paciente: " + p.getFullName() + "  ·  " + p.docType + " " + p.docNumber, 10, false)
                .paragraph("Aceptado por: " + r.get("acceptedBy"), 10, false)
                .paragraph("Fecha y hora: " + (at == null ? "-" : at.format(DMY)) + "  ·  Canal: " + canal + "  ·  Versión " + r.get("version"), 10, false)
                .space(8)
                .paragraph(String.valueOf(r.get("body") == null ? "" : r.get("body")), 9.5f, false)
                .space(14)
                .paragraph("Esta constancia se genera a partir del registro electrónico de la aceptación. Puede revocar su consentimiento o "
                        + "ejercer sus derechos de acceso, rectificación, cancelación y oposición escribiendo a la clínica.", 8.5f, false);
        return pdf.build();
    }

    // ------------------------------------------------------------------ archivos solicitados

    public record NewFileRequest(String title, String description, String kind, Long planId, java.time.LocalDate dueDate) {}

    private List<Map<String, Object>> fileRequests(Long patientId) {
        return jdbc.queryForList("""
                select fr.id, fr.title, fr.description, fr.kind, fr.status, fr.due_date as dueDate, fr.file_id as fileId,
                       fr.requested_by as requestedBy, fr.requested_at as requestedAt, fr.fulfilled_by as fulfilledBy, fr.fulfilled_at as fulfilledAt,
                       fr.plan_id as planId, tp.title as planTitle
                  from file_requests fr left join treatment_plans tp on tp.id = fr.plan_id
                 where fr.patient_id = ? and fr.status <> 'ANULADO' order by fr.status = 'PENDIENTE' desc, fr.requested_at desc""", patientId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myFileRequests(String subject) {
        return fileRequests(me(subject).id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> staffFileRequests(Long patientId) {
        return fileRequests(patientId);
    }

    @Transactional
    public List<Map<String, Object>> createRequest(Long patientId, NewFileRequest r) {
        patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        String title = r.title() == null ? "" : r.title().trim();
        if (title.length() < 3) throw ApiException.field("title", "Indica qué archivo necesitas (por ejemplo, radiografía panorámica).");
        String kind = r.kind() == null || r.kind().isBlank() ? "RADIOGRAFIA" : r.kind().trim().toUpperCase();
        if (!FileService.KINDS.contains(kind)) kind = "DOCUMENTO";
        if (r.planId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from treatment_plans where id = ? and patient_id = ?", Integer.class, r.planId(), patientId);
            if (n == null || n == 0) throw ApiException.field("planId", "Ese plan no es de este paciente.");
        }
        jdbc.update("""
                insert into file_requests (patient_id, plan_id, title, description, kind, status, due_date, requested_by, requested_at)
                values (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?)""", patientId, r.planId(), title.length() > 150 ? title.substring(0, 150) : title,
                r.description() == null || r.description().isBlank() ? null : r.description().trim(), kind, r.dueDate(),
                CurrentUser.usernameOrSystem(), Dates.now());
        audit.record("PACIENTE", patientId, "ARCHIVO_SOLICITADO", title);
        return fileRequests(patientId);
    }

    @Transactional
    public List<Map<String, Object>> cancelRequest(Long patientId, Long requestId) {
        int n = jdbc.update("update file_requests set status = 'ANULADO' where id = ? and patient_id = ? and status = 'PENDIENTE'", requestId, patientId);
        if (n == 0) throw ApiException.conflict("NO_ANULABLE", "Solo se anulan pedidos pendientes.");
        return fileRequests(patientId);
    }

    private List<Map<String, Object>> fulfill(Long patientId, Long requestId, MultipartFile file, String by) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from file_requests where id = ? and patient_id = ? for update", requestId, patientId);
        if (rows.isEmpty()) throw ApiException.notFound("El pedido de archivo");
        Map<String, Object> fr = rows.get(0);
        if ("ANULADO".equals(fr.get("status"))) throw ApiException.conflict("ANULADO", "Ese pedido fue anulado.");
        // Lo que sube el paciente queda compartido con él (es suyo) y visible para el odontólogo en la ficha.
        PatientFile f = files.upload(patientId, file, (String) fr.get("kind"), (String) fr.get("title"),
                "Subido por " + by + " para el pedido #" + requestId, null, true);
        jdbc.update("update file_requests set status = 'RECIBIDO', file_id = ?, fulfilled_by = ?, fulfilled_at = ? where id = ?",
                f.id, by, Dates.now(), requestId);
        audit.record("PACIENTE", patientId, "ARCHIVO_RECIBIDO", fr.get("title") + " · por " + by);
        return fileRequests(patientId);
    }

    @Transactional
    public List<Map<String, Object>> patientUpload(String subject, Long requestId, MultipartFile file) {
        Patient p = me(subject);
        return fulfill(p.id, requestId, file, "el paciente");
    }

    @Transactional
    public List<Map<String, Object>> staffUpload(Long patientId, Long requestId, MultipartFile file) {
        return fulfill(patientId, requestId, file, CurrentUser.usernameOrSystem());
    }

    /** Enlace temporal para que el paciente vea un archivo suyo (copia firmada o archivo que subió). */
    @Transactional(readOnly = true)
    public Map<String, String> fileLink(String subject, Long fileId) {
        Patient p = me(subject);
        PatientFile f = files.get(fileId);
        boolean own = f.patientId.equals(p.id) && (f.sharedWithPatient || Objects.equals(p.consentFileId, f.id)
                || Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from file_requests where file_id = ?", Boolean.class, f.id))
                || Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from consent_records where file_id = ?", Boolean.class, f.id)));
        if (!own) throw ApiException.notFound("El documento");
        return Map.of("url", files.link(f.id));
    }
}
