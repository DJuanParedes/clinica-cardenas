package pe.clinicacardenas.consent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.files.SimplePdf;
import pe.clinicacardenas.messaging.SurveyLinks;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v2.8: consentimientos informados con plantillas editables y firma a distancia.
 * Se envía un enlace firmado (HMAC) por WhatsApp o correo, o se abre en la tablet de la clínica; el paciente o su
 * apoderado lee el texto, escribe su nombre y DNI, marca que leyó y firma con el dedo. Se genera un PDF con la
 * firma y la constancia (fecha y hora, IP, a dónde se envió el enlace y el SHA-256 del texto) en su ficha.
 */
@Service
public class InformedConsentService {
    private static final String CONSENTIMIENTO = "CONSENTIMIENTO";
    private static final String EMAIL = "EMAIL";
    private static final String HISTORIA = "HISTORIA";
    private static final String PACIENTE = "PACIENTE";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String PRESENCIAL = "PRESENCIAL";
    private static final String WHATSAPP = "WHATSAPP";
    private static final String ACTIVE = "active";
    private static final String CHANNEL = "channel";
    private static final String PACIENTE_2 = "paciente";
    private static final String PATIENT_ID = "patient_id";
    private static final String RELATIONSHIP = "relationship";
    private static final String SIGNATUREJPEG = "signatureJpeg";
    private static final String SIGNERDOC = "signerDoc";
    private static final String STATUS = "status";
    private static final String TITLE = "title";


    public static final List<String> VARIABLES = List.of("firmante", "documento_firmante", "condicion", PACIENTE_2, "documento",
            "odontologo", "clinica", "procedimiento", "pieza", "fecha");
    public static final int VALID_DAYS = 7;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> REL = Map.of(PACIENTE, PACIENTE_2, "MADRE", "madre", "PADRE", "padre", "TUTOR", "tutor(a) legal");

    private final JdbcTemplate jdbc;
    private final PatientRepository patients;
    private final FileService files;
    private final NotificationService notifications;
    private final SurveyLinks links;
    private final AuditService audit;
    private final byte[] key;
    private final String clinicName;
    private final String clinicAddress;

    public InformedConsentService(JdbcTemplate jdbc, PatientRepository patients, FileService files, NotificationService notifications, SurveyLinks links,
                          AuditService audit, @Value("${app.jwt.secret}") String secret,
                          @Value("${app.company.trade-name}") String clinicName, @Value("${app.company.address:}") String clinicAddress) {
        this.jdbc = jdbc;
        this.patients = patients;
        this.files = files;
        this.notifications = notifications;
        this.links = links;
        this.audit = audit;
        this.key = ("consentimiento:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
    }

    public record TemplateRequest(String name, String body, Boolean active) {}

    public record SendRequest(Long templateId, String procedure, Integer tooth, Long dentistId, String channel) {}

    public record SignRequest(String signerName, String signerDoc, String relationship, Boolean accepted, String signatureJpeg) {}

    // ------------------------------------------------------------------ plantillas

    public List<Map<String, Object>> templates(boolean onlyActive) {
        return jdbc.queryForList("""
                select t.id, t.code, t.name, t.body, t.version, t.active, t.updated_by as updatedBy, t.updated_at as updatedAt,
                       (select count(*) from consent_requests r where r.template_id = t.id and r.status = 'FIRMADO') as signed
                  from consent_templates t %s order by t.sort_order, t.name
                """.formatted(onlyActive ? "where t.active = true" : ""));
    }

    @Transactional
    public Map<String, Object> saveTemplate(Long id, TemplateRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 3 || name.length() > 120) e.put("name", "Nombre de 3 a 120 caracteres.");
        String body = r.body() == null ? "" : r.body().trim();
        if (body.length() < 50 || body.length() > 8000) e.put("body", "El texto debe tener entre 50 y 8000 caracteres.");
        List<String> bad = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([a-z_]+)\\}").matcher(body);
        while (m.find()) if (!VARIABLES.contains(m.group(1)) && !bad.contains(m.group(1))) bad.add(m.group(1));
        if (!bad.isEmpty()) e.put("body", "Variables desconocidas: {" + String.join("}, {", bad) + "}.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la plantilla.", e);
        String by = CurrentUser.usernameOrSystem();
        if (id == null) {
            String code = "P" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
            jdbc.update("insert into consent_templates (code, name, body, sort_order, updated_by, updated_at) values (?, ?, ?, 100, ?, ?)", code, name, body, by, Dates.now());
            id = jdbc.queryForObject("select id from consent_templates where code = ?", Long.class, code);
            audit.record(CONSENTIMIENTO, id, "PLANTILLA_CREAR", name);
        } else {
            Map<String, Object> cur = template(id);
            boolean changed = !body.equals(cur.get("body"));
            jdbc.update("update consent_templates set name = ?, body = ?, active = ?, version = version + ?, updated_by = ?, updated_at = ? where id = ?",
                    name, body, r.active() == null ? cur.get(ACTIVE) : r.active(), changed ? 1 : 0, by, Dates.now(), id);
            audit.record(CONSENTIMIENTO, id, "PLANTILLA_EDITAR", name + (changed ? " · nueva versión" : ""));
        }
        return template(id);
    }

    private Map<String, Object> template(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("select id, code, name, body, version, active from consent_templates where id = ?", id);
        if (l.isEmpty()) throw ApiException.notFound("La plantilla");
        return l.get(0);
    }

    // ------------------------------------------------------------------ envío

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forPatient(Long patientId) {
        return requests("r.patient_id = ?", patientId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        return status == null || status.isBlank() ? requests("r.created_at >= ?", Dates.now().minusDays(90))
                : requests("r.status = ? and r.created_at >= ?", status.trim().toUpperCase(), Dates.now().minusDays(180));
    }

    private List<Map<String, Object>> requests(String where, Object... args) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select r.id, r.patient_id as patientId, concat(p.first_name, ' ', p.last_name) as patientName, r.title, r.status, r.channel,
                       r.sent_to as sentTo, r.expires_at as expiresAt, r.opened_at as openedAt, r.signed_at as signedAt, r.signer_name as signerName,
                       r.signer_relationship as signerRelationship, r.file_id as fileId, r.cancel_reason as cancelReason,
                       r.created_by as createdBy, r.created_at as createdAt
                  from consent_requests r join patients p on p.id = r.patient_id
                 where %s order by r.created_at desc limit 300
                """.formatted(where), args)) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            boolean expired = PENDIENTE.equals(r.get(STATUS)) && Dates.toLocalDateTime(r.get("expiresAt")).isBefore(Dates.now());
            m.put("expired", expired);
            if (PENDIENTE.equals(r.get(STATUS)) && !expired) m.put("url", url(((Number) r.get("id")).longValue()));
            out.add(m);
        }
        return out;
    }

    /** Arma el texto con los datos del paciente y envía el enlace (o lo deja listo para la tablet). */
    @Transactional
    public Map<String, Object> send(Long patientId, SendRequest r) {
        Patient p = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("El paciente"));
        if (r.templateId() == null) throw ApiException.field("templateId", "Elige el consentimiento.");
        Map<String, Object> t = template(r.templateId());
        if (!Boolean.TRUE.equals(t.get(ACTIVE)) && !(t.get(ACTIVE) instanceof Number n && n.intValue() == 1))
            throw ApiException.field("templateId", "Esa plantilla está desactivada.");
        String procedure = r.procedure() == null ? "" : r.procedure().trim();
        if (procedure.length() < 3 || procedure.length() > 300) throw ApiException.field("procedure", "Describe el procedimiento (3 a 300 caracteres).");
        if (r.tooth() != null && !((r.tooth() >= 11 && r.tooth() <= 48) || (r.tooth() >= 51 && r.tooth() <= 85)))
            throw ApiException.field("tooth", "Pieza dental inválida (notación FDI).");
        String channel = r.channel() == null ? WHATSAPP : r.channel().trim().toUpperCase();
        if (!Set.of(WHATSAPP, EMAIL, PRESENCIAL).contains(channel)) throw ApiException.field(CHANNEL, "Canal no válido.");
        boolean minor = p.isMinor();
        String phone = minor && p.guardianPhone != null && !p.guardianPhone.isBlank() ? p.guardianPhone : p.phone;
        String dest = switch (channel) {
            case WHATSAPP -> phone == null || phone.isBlank() ? null : phone;
            case EMAIL -> p.email == null || p.email.isBlank() ? null : p.email;
            default -> null;
        };
        if (!PRESENCIAL.equals(channel) && dest == null)
            throw ApiException.field(CHANNEL, WHATSAPP.equals(channel) ? "El paciente no tiene celular registrado." : "El paciente no tiene correo registrado.");
        String dentist = null;
        if (r.dentistId() != null) {
            List<String> d = jdbc.queryForList("select u.full_name from dentists d join users u on u.id = d.user_id where d.id = ?", String.class, r.dentistId());
            if (d.isEmpty()) throw ApiException.field("dentistId", "Odontólogo no válido.");
            dentist = d.get(0);
        }
        Map<String, String> v = new LinkedHashMap<>();
        String signer = minor && p.guardianName != null && !p.guardianName.isBlank() ? p.guardianName : p.getFullName();
        v.put("firmante", signer);
        v.put("documento_firmante", minor && p.guardianDocNumber != null ? "DNI " + p.guardianDocNumber : p.docType + " " + p.docNumber);
        v.put("condicion", minor ? (REL.getOrDefault(p.guardianRelationship == null ? "TUTOR" : p.guardianRelationship, "apoderado(a)") + " de " + p.getFullName() + " (menor de edad)") : PACIENTE_2);
        v.put(PACIENTE_2, p.getFullName());
        v.put("documento", p.docType + " " + p.docNumber);
        v.put("odontologo", dentist == null ? "odontólogo(a) tratante" : dentist);
        v.put("clinica", clinicName);
        v.put("procedimiento", procedure);
        v.put("pieza", r.tooth() == null ? "" : " (pieza " + r.tooth() + ")");
        v.put("fecha", Dates.today().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String body = fill((String) t.get("body"), v);
        String title = "Consentimiento informado · " + t.get("name");
        LocalDateTime now = Dates.now();
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        final String fDest = dest;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into consent_requests (patient_id, template_id, template_version, title, body, body_hash, dentist_id, status, channel, sent_to,
                                                  expires_at, created_by, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, p.id);
            ps.setLong(2, r.templateId());
            ps.setInt(3, ((Number) t.get("version")).intValue());
            ps.setString(4, title.length() > 150 ? title.substring(0, 150) : title);
            ps.setString(5, body);
            ps.setString(6, sha256(body));
            ps.setObject(7, r.dentistId());
            ps.setString(8, channel);
            ps.setString(9, fDest);
            ps.setObject(10, now.plusDays(VALID_DAYS));
            ps.setString(11, CurrentUser.usernameOrSystem());
            ps.setObject(12, now);
            return ps;
        }, kh);
        long id = Objects.requireNonNull(kh.getKey()).longValue();
        String url = url(id);
        if (!PRESENCIAL.equals(channel)) {
            String text = "Hola " + signer.split(" ")[0] + ", " + clinicName + " te envía el *consentimiento informado* para "
                    + (minor ? p.getFullName() + ": " : "tu tratamiento: ") + procedure + ".\n\nLéelo con calma y fírmalo desde tu celular aquí:\n" + url
                    + "\n\nEl enlace vence en " + VALID_DAYS + " días. Si tienes dudas, pregúntanos antes de firmar.";
            notifications.direct(p, CONSENTIMIENTO, channel, dest, "Consentimiento informado para firmar · " + clinicName, text, "consentimiento:" + id,
                    List.of(NotificationService.WA_DOCUMENTO, signer.split(" ")[0], clinicName, String.valueOf(t.get("name")).toLowerCase(Locale.ROOT), url, String.valueOf(VALID_DAYS)));
        }
        audit.record(HISTORIA, p.id, CONSENTIMIENTO, "Envió para firmar: " + t.get("name") + " · " + (PRESENCIAL.equals(channel) ? "en la clínica" : channel.toLowerCase()));
        Map<String, Object> out = new LinkedHashMap<>(requests("r.id = ?", id).get(0));
        out.put("url", url);
        return out;
    }

    @Transactional
    public Map<String, Object> cancel(Long id, String reason) {
        Map<String, Object> r = one(id);
        if (!PENDIENTE.equals(r.get(STATUS))) throw ApiException.conflict("CONSENTIMIENTO_CERRADO", "El consentimiento ya se firmó o se anuló.");
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) throw ApiException.field("reason", "Indica por qué se anula.");
        jdbc.update("update consent_requests set status = 'ANULADO', cancel_reason = ? where id = ?", why.length() > 200 ? why.substring(0, 200) : why, id);
        audit.record(HISTORIA, ((Number) r.get(PATIENT_ID)).longValue(), CONSENTIMIENTO, "Anuló el envío de " + r.get(TITLE) + ": " + why);
        return requests("r.id = ?", id).get(0);
    }

    // ------------------------------------------------------------------ firma pública

    /** Lo que ve quien abre el enlace. Marca la primera apertura. */
    @Transactional
    public Map<String, Object> open(String token) {
        Map<String, Object> r = one(resolve(token));
        Patient p = patients.findById(((Number) r.get(PATIENT_ID)).longValue()).orElseThrow();
        if (r.get("opened_at") == null) jdbc.update("update consent_requests set opened_at = ? where id = ?", Dates.now(), r.get("id"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clinic", clinicName);
        m.put(TITLE, r.get(TITLE));
        m.put("body", r.get("body"));
        m.put(STATUS, r.get(STATUS));
        m.put("expired", PENDIENTE.equals(r.get(STATUS)) && Dates.toLocalDateTime(r.get("expires_at")).isBefore(Dates.now()));
        m.put("patientName", p.getFullName());
        m.put("minor", p.isMinor());
        m.put("signerName", p.isMinor() ? (p.guardianName == null ? "" : p.guardianName) : p.getFullName());
        m.put(SIGNERDOC, p.isMinor() ? (p.guardianDocNumber == null ? "" : p.guardianDocNumber) : p.docNumber);
        m.put(RELATIONSHIP, p.isMinor() ? (p.guardianRelationship == null ? "" : p.guardianRelationship) : PACIENTE);
        m.put("signedAt", r.get("signed_at"));
        m.put("hash", ((String) r.get("body_hash")).substring(0, 16));
        return m;
    }

    @Transactional
    public Map<String, Object> sign(String token, SignRequest s, String ip, String agent) {
        Long id = resolve(token);
        Map<String, Object> r = jdbc.queryForList("select * from consent_requests where id = ? for update", id).get(0);
        if ("FIRMADO".equals(r.get(STATUS))) throw ApiException.conflict("YA_FIRMADO", "Este consentimiento ya fue firmado.");
        if (!PENDIENTE.equals(r.get(STATUS))) throw ApiException.conflict("ANULADO", "Este consentimiento fue anulado por la clínica.");
        if (Dates.toLocalDateTime(r.get("expires_at")).isBefore(Dates.now()))
            throw ApiException.conflict("VENCIDO", "El enlace venció. Pide a la clínica que te lo envíe de nuevo.");
        Patient p = patients.findById(((Number) r.get(PATIENT_ID)).longValue()).orElseThrow();
        Map<String, String> e = new LinkedHashMap<>();
        String name = s.signerName() == null ? "" : s.signerName().trim().replaceAll("\\s+", " ");
        if (name.length() < 5 || name.length() > 120) e.put("signerName", "Escribe tu nombre completo.");
        String doc = s.signerDoc() == null ? "" : s.signerDoc().trim();
        if (!doc.matches("\\d{8}|[A-Za-z0-9]{9,12}")) e.put(SIGNERDOC, "Escribe tu DNI (8 dígitos) o carné de extranjería.");
        String rel = s.relationship() == null || s.relationship().isBlank() ? PACIENTE : s.relationship().trim().toUpperCase();
        if (!REL.containsKey(rel)) e.put(RELATIONSHIP, "Indica quién firma.");
        if (p.isMinor() && PACIENTE.equals(rel)) e.put(RELATIONSHIP, "El paciente es menor de edad: firma su madre, padre o tutor.");
        if (!p.isMinor() && !PACIENTE.equals(rel)) e.put(RELATIONSHIP, "El paciente es mayor de edad: debe firmar él mismo.");
        if (PACIENTE.equals(rel) && !doc.equals(p.docNumber)) e.put(SIGNERDOC, "El documento no coincide con el del paciente.");
        if (!Boolean.TRUE.equals(s.accepted())) e.put("accepted", "Marca que leíste y entendiste el documento.");
        byte[] sig = null;
        if (s.signatureJpeg() == null || s.signatureJpeg().isBlank()) e.put(SIGNATUREJPEG, "Firma en el recuadro.");
        else {
            String b64 = s.signatureJpeg().contains(",") ? s.signatureJpeg().substring(s.signatureJpeg().indexOf(',') + 1) : s.signatureJpeg();
            try { sig = Base64.getDecoder().decode(b64); } catch (IllegalArgumentException ex) { e.put(SIGNATUREJPEG, "La firma no es válida."); }
            if (sig != null && (sig.length > 400_000 || SimplePdf.jpegSize(sig) == null)) e.put(SIGNATUREJPEG, "La firma no es válida.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos antes de firmar.", e);
        LocalDateTime now = Dates.now();
        String sentTo = (String) r.get("sent_to");
        String via = switch ((String) r.get(CHANNEL)) {
            case WHATSAPP -> "enlace enviado por WhatsApp al ***" + (sentTo == null ? "" : sentTo.substring(Math.max(0, sentTo.length() - 3)));
            case EMAIL -> "enlace enviado por correo a " + mask(sentTo);
            default -> "firmado en la clínica (tablet)";
        };
        SimplePdf pdf = new SimplePdf()
                .header(clinicName, clinicAddress)
                .title((String) r.get(TITLE))
                .paragraph("Paciente: " + p.getFullName() + "    " + p.docType + " " + p.docNumber
                        + (p.hcNumber == null ? "" : "    HC " + p.hcNumber), 10, false)
                .space(8)
                .paragraph((String) r.get("body"), 10.5f, false)
                .space(18);
        pdf.jpeg(sig, 200);
        pdf.signatureLine("Firma: " + name + " · DNI/CE " + doc + (PACIENTE.equals(rel) ? "" : " · " + REL.get(rel) + " del paciente"));
        pdf.space(10).paragraph("Constancia de firma electrónica: firmado el " + now.format(DMY) + " · " + via + " · IP " + (ip == null ? "-" : ip)
                + " · código de verificación del texto (SHA-256): " + r.get("body_hash"), 8, false);
        PatientFile f = files.storeSigned(p.id, pdf.build(), CONSENTIMIENTO, (String) r.get(TITLE), now);
        jdbc.update("""
                update consent_requests set status = 'FIRMADO', signed_at = ?, signer_name = ?, signer_doc = ?, signer_relationship = ?,
                       signer_ip = ?, signer_agent = ?, file_id = ? where id = ?
                """, now, name, doc, rel, ip, agent == null ? null : agent.substring(0, Math.min(250, agent.length())), f.id, id);
        audit.record(HISTORIA, p.id, CONSENTIMIENTO, "Firmó a distancia: " + r.get(TITLE) + " · " + name + " (" + REL.get(rel) + ")");
        return Map.of("message", "¡Listo! Tu consentimiento quedó firmado. La clínica ya lo tiene en tu ficha.", "signedAt", now);
    }

    // ------------------------------------------------------------------ utilidades

    private Map<String, Object> one(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("select * from consent_requests where id = ?", id);
        if (l.isEmpty()) throw ApiException.notFound("El consentimiento");
        return l.get(0);
    }

    public String url(long id) {
        String i = Long.toString(id, 36);
        return links.base() + "/firmar/" + i + "." + hmac(i);
    }

    private Long resolve(String token) {
        if (token == null || !token.matches("^[0-9a-z]{1,13}\\.[A-Za-z0-9_-]{20}$")) throw ApiException.notFound("El enlace");
        String[] p = token.split("\\.");
        if (!MessageDigest.isEqual(hmac(p[0]).getBytes(StandardCharsets.UTF_8), p[1].getBytes(StandardCharsets.UTF_8))) throw ApiException.notFound("El enlace");
        return Long.parseLong(p[0], 36);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String fill(String text, Map<String, String> v) {
        String out = text;
        for (var e : v.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return out;
    }

    private static String mask(String email) {
        if (email == null || !email.contains("@")) return "-";
        String[] p = email.split("@");
        return p[0].charAt(0) + "***@" + p[1];
    }
}
