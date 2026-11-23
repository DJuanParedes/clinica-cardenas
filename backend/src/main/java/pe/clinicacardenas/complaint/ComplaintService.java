package pe.clinicacardenas.complaint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.auth.MailNotifier;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.crm.TaskService;
import pe.clinicacardenas.files.SimplePdf;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * v2.9: Libro de Reclamaciones virtual.
 * <p>
 * Cumple lo mínimo del Anexo I del DS 011-2011-PCM: datos del proveedor (razón social, RUC, dirección), número
 * correlativo y código de identificación, datos del consumidor (y del padre/madre o apoderado si es menor), bien o
 * servicio contratado, monto, tipo (reclamo o queja), detalle, pedido y observaciones del proveedor. Al registrarse
 * se envía una copia en PDF al correo del consumidor. El plazo de respuesta se cuenta en días hábiles (lunes a
 * viernes sin feriados nacionales). No se condiciona el registro a aceptar la política de privacidad.
 */
@Service
public class ComplaintService {

    public static final List<String> KINDS = List.of("RECLAMO", "QUEJA");
    public static final List<String> DOC_TYPES = List.of("DNI", "CE", "PASAPORTE", "RUC");
    public static final List<String> STATUSES = List.of("PENDIENTE", "EN_PROCESO", "RESPONDIDO");
    public static final List<String> CHANNELS = List.of("EMAIL", "DOMICILIO", "PRESENCIAL");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMYH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern EMAIL = pe.clinicacardenas.common.Emails.PATTERN;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final MailNotifier mail;
    private final TaskService tasks;
    private final AuditService audit;
    private final String ruc, businessName, tradeName, address;

    public ComplaintService(JdbcTemplate jdbc, MailNotifier mail, TaskService tasks, AuditService audit,
                            @Value("${app.company.ruc}") String ruc, @Value("${app.company.business-name}") String businessName,
                            @Value("${app.company.trade-name}") String tradeName, @Value("${app.company.address:}") String address) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.tasks = tasks;
        this.audit = audit;
        this.ruc = ruc;
        this.businessName = businessName;
        this.tradeName = tradeName;
        this.address = address;
    }

    public record FileRequest(String kind, String fullName, String docType, String docNumber, String address, String phone,
                              String email, Boolean minor, String guardianName, String guardianDoc, String service,
                              BigDecimal amount, String detail, String request) {}

    public record UpdateRequest(String status, String providerNotes) {}

    public record RespondRequest(String response, String channel, String providerNotes, Boolean sendEmail) {}

    // ------------------------------------------------------------------ datos del proveedor y plazo

    public int termDays() {
        try {
            return Math.max(1, Integer.parseInt(setting("reclamos_plazo_dias", "15")));
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", businessName);
        m.put("tradeName", tradeName);
        m.put("ruc", ruc);
        m.put("address", address);
        m.put("termDays", termDays());
        m.put("today", Dates.today());
        m.put("nextNumber", String.format("%d-%06d", Dates.today().getYear(), nextNumber(Dates.today().getYear(), false)));
        return m;
    }

    // ------------------------------------------------------------------ registro público

    @Transactional
    public Map<String, Object> file(FileRequest r, String ip, String agent) {
        Map<String, String> e = new LinkedHashMap<>();
        String kind = up(r.kind());
        if (!KINDS.contains(kind)) e.put("kind", "Elige si es un reclamo o una queja.");
        String name = clean(r.fullName(), 150);
        if (name == null || name.length() < 5 || !name.contains(" ")) e.put("fullName", "Escribe tus nombres y apellidos.");
        String docType = up(r.docType());
        String doc = r.docNumber() == null ? "" : r.docNumber().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!DOC_TYPES.contains(docType)) e.put("docType", "Elige el tipo de documento.");
        else if (!validDoc(docType, doc)) e.put("docNumber", "El número de documento no es válido.");
        String addr = clean(r.address(), 250);
        if (addr == null || addr.length() < 5) e.put("address", "Escribe tu domicilio.");
        String phone = clean(r.phone(), 20);
        if (phone != null && !phone.replaceAll("[^0-9]", "").matches("\\d{6,12}")) e.put("phone", "El teléfono no es válido.");
        String email = clean(r.email(), 120);
        if (email != null && !EMAIL.matcher(email).matches()) e.put("email", "El correo no es válido.");
        boolean minor = Boolean.TRUE.equals(r.minor());
        String gName = minor ? clean(r.guardianName(), 150) : null;
        String gDoc = minor && r.guardianDoc() != null ? r.guardianDoc().replaceAll("\\s", "") : null;
        if (minor && (gName == null || gName.length() < 5)) e.put("guardianName", "Escribe el nombre del padre, madre o apoderado.");
        if (minor && (gDoc == null || !gDoc.matches("[0-9A-Za-z]{8,12}"))) e.put("guardianDoc", "Escribe el documento del padre, madre o apoderado.");
        String service = clean(r.service(), 250);
        if (service == null || service.length() < 3) e.put("service", "Indica el servicio o tratamiento.");
        BigDecimal amount = r.amount();
        if (amount != null && (amount.signum() < 0 || amount.compareTo(new BigDecimal("1000000")) > 0)) e.put("amount", "El monto no es válido.");
        String detail = clean(r.detail(), 4000);
        if (detail == null || detail.length() < 15) e.put("detail", "Cuéntanos qué pasó (mínimo 15 caracteres).");
        String request = clean(r.request(), 2000);
        if (request == null || request.length() < 5) e.put("request", "Indica qué solicitas.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos marcados.", e);

        LocalDateTime now = Dates.now();
        int year = now.getYear();
        int number = nextNumber(year, true);
        String code = newCode();
        LocalDate due = addBusinessDays(now.toLocalDate(), termDays());
        Long patientId = jdbc.query("select id from patients where doc_number = ? order by id limit 1",
                rs -> rs.next() ? rs.getLong(1) : null, doc);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into complaints (year, number, code, kind, filed_at, full_name, doc_type, doc_number, address, phone, email,
                      is_minor, guardian_name, guardian_doc, service, amount, detail, request, patient_id, status, due_date, ip, user_agent)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDIENTE',?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setInt(i++, year);
            ps.setInt(i++, number);
            ps.setString(i++, code);
            ps.setString(i++, kind);
            ps.setObject(i++, now);
            ps.setString(i++, name);
            ps.setString(i++, docType);
            ps.setString(i++, doc);
            ps.setString(i++, addr);
            ps.setString(i++, phone);
            ps.setString(i++, email);
            ps.setBoolean(i++, minor);
            ps.setString(i++, gName);
            ps.setString(i++, gDoc);
            ps.setString(i++, service);
            ps.setBigDecimal(i++, amount);
            ps.setString(i++, detail);
            ps.setString(i++, request);
            ps.setObject(i++, patientId);
            ps.setObject(i++, due);
            ps.setString(i++, ip);
            ps.setString(i, agent == null ? null : agent.substring(0, Math.min(250, agent.length())));
            return ps;
        }, kh);
        long id = Objects.requireNonNull(kh.getKey()).longValue();
        String label = label(year, number);

        // Tarea para recepción/dirección: vence 2 días hábiles antes del plazo legal.
        LocalDate taskDue = subtractBusinessDays(due, 2);
        if (taskDue.isBefore(now.toLocalDate())) taskDue = now.toLocalDate();
        Long taskId = tasks.insert(new TaskService.NewTask(patientId, null, "OTRO",
                "Responder " + kind.toLowerCase(Locale.ROOT) + " N.° " + label + " (" + name + ")",
                "Libro de reclamaciones. Plazo legal: " + due.format(DMY) + ". Pedido: " + cut(request, 300),
                null, taskDue, null, "ALTA", "RECLAMO", label), "sistema");
        jdbc.update("update complaints set task_id = ? where id = ?", taskId, id);
        audit.record("RECLAMO", id, "REGISTRAR", kind + " " + label + " · " + name);

        Map<String, Object> c = get(id);
        boolean sent = false;
        if (email != null) {
            sent = mail.sendHtml(email, "Hoja de " + kind.toLowerCase(Locale.ROOT) + " N.° " + label + " · " + tradeName,
                    "<p>Hola " + esc(name.split(" ")[0]) + ",</p><p>Registramos tu " + kind.toLowerCase(Locale.ROOT)
                            + " en el Libro de Reclamaciones de " + esc(tradeName) + " con el <b>N.° " + label + "</b> (código de consulta <b>"
                            + code + "</b>). Adjuntamos la copia de tu hoja.</p><p>Te responderemos a más tardar el <b>"
                            + due.format(DMY) + "</b>.</p><p>Atentamente,<br>" + esc(businessName) + " · RUC " + ruc + "</p>",
                    "hoja-reclamacion-" + label + ".pdf", pdf(c), "application/pdf");
            if (sent) jdbc.update("update complaints set copy_sent = true where id = ?", id);
        }
        String notice = setting("reclamos_correo_aviso", "");
        if (!notice.isBlank()) {
            mail.send(notice, "Nuevo " + kind.toLowerCase(Locale.ROOT) + " N.° " + label + " en el Libro de Reclamaciones",
                    name + " (" + docType + " " + doc + ") registró un " + kind.toLowerCase(Locale.ROOT) + ".\n\nServicio: " + service
                            + "\nDetalle: " + detail + "\nPedido: " + request + "\n\nPlazo para responder: " + due.format(DMY));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", label);
        out.put("code", code);
        out.put("kind", kind);
        out.put("filedAt", now);
        out.put("dueDate", due);
        out.put("copySent", sent);
        out.put("email", email);
        return out;
    }

    /** Consulta pública: código + número de documento. */
    public Map<String, Object> lookup(String code, String doc) {
        Map<String, Object> c = byCode(code, doc);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : List.of("number", "code", "kind", "filedAt", "dueDate", "status", "service", "detail", "request", "response", "respondedAt"))
            out.put(k, c.get(k));
        if (!"RESPONDIDO".equals(c.get("status"))) out.put("response", null);
        return out;
    }

    public Map<String, Object> byCode(String code, String doc) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String d = doc == null ? "" : doc.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        List<Long> ids = jdbc.queryForList("select id from complaints where code = ? and doc_number = ?", Long.class, c, d);
        if (ids.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No encontramos una hoja con ese código y documento.");
        return get(ids.get(0));
    }

    // ------------------------------------------------------------------ módulo interno

    public List<Map<String, Object>> list(String status, Integer year, String q) {
        StringBuilder w = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            if ("VENCIDOS".equals(status)) {
                w.append(" and c.status <> 'RESPONDIDO' and c.due_date < ?");
                args.add(Dates.today());
            } else if ("ABIERTOS".equals(status)) {
                w.append(" and c.status <> 'RESPONDIDO'");
            } else {
                w.append(" and c.status = ?");
                args.add(status);
            }
        }
        if (year != null) {
            w.append(" and c.year = ?");
            args.add(year);
        }
        if (q != null && !q.isBlank()) {
            w.append(" and (c.full_name like ? or c.doc_number like ? or c.code like ? or concat(c.year,'-',lpad(c.number,6,'0')) like ?)");
            String like = "%" + q.trim() + "%";
            args.addAll(List.of(like, like, like, like));
        }
        return jdbc.query(SELECT + w + " order by c.status = 'RESPONDIDO', c.due_date, c.id desc", (rs, i) -> row(rs), args.toArray());
    }

    public Map<String, Object> summary() {
        LocalDate today = Dates.today();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", jdbc.queryForObject("select count(*) from complaints where status <> 'RESPONDIDO'", Long.class));
        m.put("overdue", jdbc.queryForObject("select count(*) from complaints where status <> 'RESPONDIDO' and due_date < ?", Long.class, today));
        m.put("dueSoon", jdbc.queryForObject("select count(*) from complaints where status <> 'RESPONDIDO' and due_date between ? and ?",
                Long.class, today, addBusinessDays(today, 3)));
        m.put("year", jdbc.queryForObject("select count(*) from complaints where year = ?", Long.class, today.getYear()));
        m.put("termDays", termDays());
        m.put("noticeEmail", setting("reclamos_correo_aviso", ""));
        return m;
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.query(SELECT + " where c.id = ?", (rs, i) -> row(rs), id);
        if (l.isEmpty()) throw ApiException.notFound("La hoja de reclamación");
        return l.get(0);
    }

    @Transactional
    public Map<String, Object> update(Long id, UpdateRequest r) {
        Map<String, Object> c = get(id);
        if ("RESPONDIDO".equals(c.get("status"))) throw ApiException.badRequest("YA_RESPONDIDO", "Esta hoja ya fue respondida.");
        String st = r.status() == null ? (String) c.get("status") : up(r.status());
        if (!List.of("PENDIENTE", "EN_PROCESO").contains(st)) throw ApiException.field("status", "Estado no válido.");
        jdbc.update("update complaints set status = ?, provider_notes = ? where id = ?", st, clean(r.providerNotes(), 4000), id);
        audit.record("RECLAMO", id, "ACTUALIZAR", c.get("number") + " · " + st);
        return get(id);
    }

    @Transactional
    public Map<String, Object> respond(Long id, RespondRequest r) {
        Map<String, Object> c = get(id);
        if ("RESPONDIDO".equals(c.get("status"))) throw ApiException.badRequest("YA_RESPONDIDO", "Esta hoja ya fue respondida.");
        String response = clean(r.response(), 6000);
        if (response == null || response.length() < 20) throw ApiException.field("response", "Escribe la respuesta (mínimo 20 caracteres).");
        String channel = up(r.channel());
        if (!CHANNELS.contains(channel)) throw ApiException.field("channel", "Indica cómo se entregó la respuesta.");
        String email = (String) c.get("email");
        if ("EMAIL".equals(channel) && (email == null || email.isBlank()))
            throw ApiException.field("channel", "El consumidor no dejó correo: entrégala en su domicilio o en la clínica.");
        LocalDateTime now = Dates.now();
        jdbc.update("""
                update complaints set status = 'RESPONDIDO', response = ?, response_channel = ?, responded_at = ?, responded_by = ?,
                       provider_notes = coalesce(?, provider_notes) where id = ?
                """, response, channel, now, CurrentUser.usernameOrSystem(), clean(r.providerNotes(), 4000), id);
        Object taskId = c.get("taskId");
        if (taskId != null)
            jdbc.update("update tasks set status = 'HECHA', result = 'Respondido', completed_at = ?, completed_by = ? where id = ? and status = 'PENDIENTE'",
                    now, CurrentUser.usernameOrSystem(), ((Number) taskId).longValue());
        Map<String, Object> done = get(id);
        if ("EMAIL".equals(channel) && !Boolean.FALSE.equals(r.sendEmail())) {
            boolean ok = mail.sendHtml(email, "Respuesta a tu " + ((String) c.get("kind")).toLowerCase(Locale.ROOT) + " N.° " + c.get("number") + " · " + tradeName,
                    "<p>Hola " + esc(((String) c.get("fullName")).split(" ")[0]) + ",</p><p>Te enviamos la respuesta a tu "
                            + ((String) c.get("kind")).toLowerCase(Locale.ROOT) + " N.° " + c.get("number") + ":</p><blockquote>"
                            + esc(response).replace("\n", "<br>") + "</blockquote><p>Adjuntamos la hoja completa.</p><p>Atentamente,<br>"
                            + esc(businessName) + " · RUC " + ruc + "</p>",
                    "respuesta-reclamacion-" + c.get("number") + ".pdf", pdf(done), "application/pdf");
            if (ok) jdbc.update("update complaints set response_sent = true where id = ?", id);
        }
        audit.record("RECLAMO", id, "RESPONDER", c.get("number") + " · " + channel.toLowerCase(Locale.ROOT));
        return get(id);
    }

    @Transactional
    public Map<String, Object> settings(Map<String, String> body) {
        if (body.containsKey("termDays")) {
            int d;
            try {
                d = Integer.parseInt(body.get("termDays").trim());
            } catch (Exception e) {
                throw ApiException.field("termDays", "Escribe un número de días.");
            }
            if (d < 1 || d > 30) throw ApiException.field("termDays", "Entre 1 y 30 días hábiles.");
            putSetting("reclamos_plazo_dias", String.valueOf(d));
        }
        if (body.containsKey("noticeEmail")) {
            String m = body.get("noticeEmail") == null ? "" : body.get("noticeEmail").trim();
            if (!m.isEmpty() && !EMAIL.matcher(m).matches()) throw ApiException.field("noticeEmail", "El correo no es válido.");
            putSetting("reclamos_correo_aviso", m);
        }
        audit.record("RECLAMO", null, "CONFIGURAR", "Plazo " + termDays() + " días hábiles");
        return summary();
    }

    /** Reporte para INDECOPI/SUSALUD: todas las hojas del año en CSV (separado por punto y coma, abre en Excel). */
    public String csv(int year) {
        StringBuilder sb = new StringBuilder("﻿N.°;Código;Tipo;Fecha;Consumidor;Documento;Menor de edad;Apoderado;Domicilio;Teléfono;Correo;"
                + "Servicio;Monto;Detalle;Pedido;Estado;Plazo;Respondido el;Medio;Respuesta;Observaciones del proveedor\n");
        for (Map<String, Object> c : list(null, year, null)) {
            sb.append(String.join(";", List.of(
                    s(c.get("number")), s(c.get("code")), s(c.get("kind")), fmt(c.get("filedAt")), s(c.get("fullName")),
                    s(c.get("docType")) + " " + s(c.get("docNumber")), Boolean.TRUE.equals(c.get("minor")) ? "Sí" : "No",
                    s(c.get("guardianName")), s(c.get("address")), s(c.get("phone")), s(c.get("email")), s(c.get("service")),
                    s(c.get("amount")), s(c.get("detail")), s(c.get("request")), s(c.get("status")), fmt(c.get("dueDate")),
                    fmt(c.get("respondedAt")), s(c.get("responseChannel")), s(c.get("response")), s(c.get("providerNotes")))
                    .stream().map(ComplaintService::csvCell).toList())).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ PDF de la hoja

    public byte[] pdf(Map<String, Object> c) {
        boolean minor = Boolean.TRUE.equals(c.get("minor"));
        SimplePdf p = new SimplePdf()
                .header(businessName, "RUC " + ruc + " · " + address)
                .title("LIBRO DE RECLAMACIONES · HOJA DE RECLAMACIÓN N.° " + c.get("number"))
                .paragraph("Código de identificación: " + c.get("code") + "      Fecha: " + fmt(c.get("filedAt")), 10, true)
                .space(6)
                .paragraph("1. IDENTIFICACIÓN DEL CONSUMIDOR RECLAMANTE", 10, true)
                .paragraph("Nombre: " + c.get("fullName") + "\n" + c.get("docType") + ": " + c.get("docNumber")
                        + "\nDomicilio: " + c.get("address")
                        + "\nTeléfono: " + s(c.get("phone")) + "      Correo: " + s(c.get("email"))
                        + (minor ? "\nMenor de edad. Padre, madre o apoderado: " + c.get("guardianName") + " · Doc. " + c.get("guardianDoc") : ""), 10, false)
                .space(6)
                .paragraph("2. IDENTIFICACIÓN DEL BIEN CONTRATADO", 10, true)
                .paragraph("Servicio: " + c.get("service") + (c.get("amount") == null ? "" : "\nMonto reclamado: S/ " + c.get("amount")), 10, false)
                .space(6)
                .paragraph("3. DETALLE DE LA RECLAMACIÓN Y PEDIDO DEL CONSUMIDOR", 10, true)
                .paragraph("Tipo: " + c.get("kind") + ("RECLAMO".equals(c.get("kind"))
                        ? " (disconformidad relacionada con el servicio)" : " (disconformidad no relacionada con el servicio, o malestar respecto a la atención)"), 10, false)
                .paragraph("Detalle: " + c.get("detail"), 10, false)
                .paragraph("Pedido: " + c.get("request"), 10, false)
                .space(6)
                .paragraph("4. OBSERVACIONES Y ACCIONES ADOPTADAS POR EL PROVEEDOR", 10, true);
        if ("RESPONDIDO".equals(c.get("status"))) {
            if (c.get("providerNotes") != null) p.paragraph(s(c.get("providerNotes")), 10, false);
            p.paragraph("Respuesta (" + fmt(c.get("respondedAt")) + ", " + s(c.get("responseChannel")).toLowerCase(Locale.ROOT) + "): " + c.get("response"), 10, false);
        } else {
            p.paragraph("Pendiente de respuesta. Fecha máxima de respuesta: " + fmt(c.get("dueDate")) + " (" + termDays() + " días hábiles).", 10, false);
        }
        p.space(10).paragraph("RECLAMO: disconformidad relacionada a los productos o servicios. QUEJA: disconformidad no relacionada a los "
                + "productos o servicios, o malestar o descontento respecto a la atención al público. La formulación del reclamo no impide "
                + "acudir a otras vías de solución de controversias ni es requisito previo para interponer una denuncia ante el INDECOPI "
                + "o, en servicios de salud, ante SUSALUD. El proveedor debe dar respuesta al reclamo en un plazo no mayor a "
                + termDays() + " días hábiles.", 8, false);
        return p.build();
    }

    // ------------------------------------------------------------------ recordatorio diario

    /** 8:00 de lunes a viernes: correo al aviso de la clínica con las hojas por vencer o vencidas. */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "America/Lima")
    public void dailyReminder() {
        String to = setting("reclamos_correo_aviso", "");
        if (to.isBlank()) return;
        LocalDate today = Dates.today();
        List<Map<String, Object>> l = jdbc.queryForList("""
                select concat(year,'-',lpad(number,6,'0')) n, kind, full_name, due_date from complaints
                 where status <> 'RESPONDIDO' and due_date <= ? order by due_date
                """, addBusinessDays(today, 3));
        if (l.isEmpty()) return;
        StringBuilder sb = new StringBuilder("Hojas del Libro de Reclamaciones por responder:\n\n");
        for (Map<String, Object> m : l) {
            LocalDate d = Dates.toLocalDate(m.get("due_date"));
            sb.append("· N.° ").append(m.get("n")).append(" (").append(String.valueOf(m.get("kind")).toLowerCase(Locale.ROOT)).append(") ")
                    .append(m.get("full_name")).append(" — ").append(d.isBefore(today) ? "VENCIDO el " : "vence el ").append(d.format(DMY)).append('\n');
        }
        mail.send(to, "Reclamos por vencer · " + tradeName, sb.toString());
    }

    // ------------------------------------------------------------------ días hábiles

    /** Feriados nacionales del Perú (fijos) más Jueves y Viernes Santo. */
    public static boolean holiday(LocalDate d) {
        Set<MonthDay> fixed = Set.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(6, 7), MonthDay.of(6, 29), MonthDay.of(7, 23),
                MonthDay.of(7, 28), MonthDay.of(7, 29), MonthDay.of(8, 6), MonthDay.of(8, 30), MonthDay.of(10, 8), MonthDay.of(11, 1),
                MonthDay.of(12, 8), MonthDay.of(12, 9), MonthDay.of(12, 25));
        if (fixed.contains(MonthDay.from(d))) return true;
        LocalDate easter = easter(d.getYear());
        return d.equals(easter.minusDays(2)) || d.equals(easter.minusDays(3));
    }

    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3,
                h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7,
                m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }

    public static boolean businessDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holiday(d);
    }

    /** El día de presentación no cuenta: se empieza a contar desde el día hábil siguiente. */
    public static LocalDate addBusinessDays(LocalDate from, int days) {
        LocalDate d = from;
        int n = 0;
        while (n < days) {
            d = d.plusDays(1);
            if (businessDay(d)) n++;
        }
        return d;
    }

    static LocalDate subtractBusinessDays(LocalDate from, int days) {
        LocalDate d = from;
        int n = 0;
        while (n < days) {
            d = d.minusDays(1);
            if (businessDay(d)) n++;
        }
        return d;
    }

    // ------------------------------------------------------------------ internos

    private static final String SELECT = """
            select c.*, concat(p.first_name, ' ', p.last_name) as patient_name, p.hc_number, t.status as task_status
              from complaints c
              left join patients p on p.id = c.patient_id
              left join tasks t on t.id = c.task_id
            """;

    private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("number", label(rs.getInt("year"), rs.getInt("number")));
        m.put("code", rs.getString("code"));
        m.put("kind", rs.getString("kind"));
        m.put("filedAt", rs.getObject("filed_at", LocalDateTime.class));
        m.put("fullName", rs.getString("full_name"));
        m.put("docType", rs.getString("doc_type"));
        m.put("docNumber", rs.getString("doc_number"));
        m.put("address", rs.getString("address"));
        m.put("phone", rs.getString("phone"));
        m.put("email", rs.getString("email"));
        m.put("minor", rs.getBoolean("is_minor"));
        m.put("guardianName", rs.getString("guardian_name"));
        m.put("guardianDoc", rs.getString("guardian_doc"));
        m.put("service", rs.getString("service"));
        m.put("amount", rs.getBigDecimal("amount"));
        m.put("detail", rs.getString("detail"));
        m.put("request", rs.getString("request"));
        long pid = rs.getLong("patient_id");
        m.put("patientId", rs.wasNull() ? null : pid);
        m.put("patientName", rs.getString("patient_name"));
        m.put("hcNumber", rs.getString("hc_number"));
        m.put("status", rs.getString("status"));
        LocalDate due = rs.getObject("due_date", LocalDate.class);
        m.put("dueDate", due);
        boolean open = !"RESPONDIDO".equals(rs.getString("status"));
        m.put("overdue", open && due.isBefore(Dates.today()));
        m.put("daysLeft", open ? businessDaysBetween(Dates.today(), due) : null);
        m.put("providerNotes", rs.getString("provider_notes"));
        m.put("response", rs.getString("response"));
        m.put("respondedAt", rs.getObject("responded_at", LocalDateTime.class));
        m.put("respondedBy", rs.getString("responded_by"));
        m.put("responseChannel", rs.getString("response_channel"));
        m.put("responseSent", rs.getBoolean("response_sent"));
        m.put("copySent", rs.getBoolean("copy_sent"));
        long tid = rs.getLong("task_id");
        m.put("taskId", rs.wasNull() ? null : tid);
        m.put("taskStatus", rs.getString("task_status"));
        return m;
    }

    /** Días hábiles que faltan (negativo si ya venció). */
    static long businessDaysBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) return -ChronoUnit.DAYS.between(to, from);
        long n = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) if (businessDay(d)) n++;
        return n;
    }

    private int nextNumber(int year, boolean lock) {
        Integer n = jdbc.queryForObject("select coalesce(max(number), 0) + 1 from complaints where year = ?" + (lock ? " for update" : ""),
                Integer.class, year);
        return n == null ? 1 : n;
    }

    private String newCode() {
        for (int t = 0; t < 10; t++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(RND.nextInt(CODE_CHARS.length())));
            String c = sb.toString();
            Integer n = jdbc.queryForObject("select count(*) from complaints where code = ?", Integer.class, c);
            if (n == null || n == 0) return c;
        }
        throw new IllegalStateException("No se pudo generar el código");
    }

    private static String label(int year, int number) {
        return String.format("%d-%06d", year, number);
    }

    private static boolean validDoc(String type, String doc) {
        return switch (type) {
            case "DNI" -> doc.matches("\\d{8}");
            case "RUC" -> doc.matches("(10|15|17|20)\\d{9}");
            case "CE" -> doc.matches("[0-9A-Z]{8,12}");
            default -> doc.matches("[0-9A-Z]{6,15}");
        };
    }

    private String setting(String k, String def) {
        List<String> l = jdbc.queryForList("select v from app_settings where k = ?", String.class, k);
        return l.isEmpty() || l.get(0) == null ? def : l.get(0);
    }

    private void putSetting(String k, String v) {
        jdbc.update("""
                insert into app_settings (k, v, updated_at, updated_by) values (?, ?, ?, ?)
                on duplicate key update v = values(v), updated_at = values(updated_at), updated_by = values(updated_by)
                """, k, v, Dates.now(), CurrentUser.usernameOrSystem());
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.replace("\r", "").trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String fmt(Object o) {
        if (o instanceof LocalDateTime t) return t.format(DMYH);
        if (o instanceof LocalDate d) return d.format(DMY);
        return s(o);
    }

    private static String csvCell(String v) {
        String t = v.replace("\n", " ").replace("\r", "");
        return t.contains(";") || t.contains("\"") ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
