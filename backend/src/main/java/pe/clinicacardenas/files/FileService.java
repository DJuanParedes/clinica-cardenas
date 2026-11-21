package pe.clinicacardenas.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Archivos clínicos: fotos intraorales, radiografías y documentos (subidos desde la web o la app del odontólogo),
 * y documentos generados con firma del paciente (consentimiento informado, receta, presupuesto).
 */
@Service
public class FileService {

    public static final List<String> KINDS = List.of("FOTO", "RADIOGRAFIA", "DOCUMENTO", "CONSENTIMIENTO", "RECETA", "PRESUPUESTO");
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "application/pdf", ".pdf");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PatientFileRepository files;
    private final PatientRepository patients;
    private final AuditService audit;
    private final Path dir;
    private final long maxBytes;
    private final byte[] linkKey;
    private final String clinicName, clinicAddress;

    public FileService(PatientFileRepository files, PatientRepository patients, AuditService audit,
                       @Value("${app.files.dir}") String dir, @Value("${app.files.max-mb:10}") long maxMb,
                       @Value("${app.jwt.secret}") String secret,
                       @Value("${app.company.trade-name}") String clinicName,
                       @Value("${app.company.address}") String clinicAddress) throws IOException {
        this.files = files;
        this.patients = patients;
        this.audit = audit;
        this.dir = Path.of(dir).toAbsolutePath();
        Files.createDirectories(this.dir);
        this.maxBytes = maxMb * 1024 * 1024;
        this.linkKey = ("archivos:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
    }

    public record DocumentRequest(String kind, String title, String body, String signerName, String signatureJpeg,
                                  Boolean shareWithPatient) {}

    private Patient patient(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    @Transactional(readOnly = true)
    public List<PatientFile> list(Long patientId) {
        patient(patientId);
        return files.findByPatientIdAndDeletedAtIsNullOrderByCreatedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<PatientFile> shared(Long patientId) {
        return files.findByPatientIdAndSharedWithPatientTrueAndDeletedAtIsNullOrderByCreatedAtDesc(patientId);
    }

    public PatientFile get(Long id) {
        PatientFile f = files.findById(id).orElseThrow(() -> ApiException.notFound("El archivo"));
        if (f.deletedAt != null) throw ApiException.notFound("El archivo");
        return f;
    }

    @Transactional
    public PatientFile upload(Long patientId, MultipartFile file, String kind, String title, String note, Integer tooth,
                              boolean share) {
        Patient p = patient(patientId);
        if (file == null || file.isEmpty()) throw ApiException.field("file", "Selecciona un archivo.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if ("image/jpg".equals(type)) type = "image/jpeg";
        if (!TYPES.containsKey(type)) throw ApiException.field("file", "Solo se aceptan fotos (JPG, PNG, WEBP) o PDF.");
        if (file.getSize() > maxBytes) throw ApiException.field("file", "El archivo supera " + (maxBytes / 1024 / 1024) + " MB.");
        String k = normalizeKind(kind, type);
        String t = title == null || title.isBlank() ? defaultTitle(k, file.getOriginalFilename()) : title.trim();
        if (t.length() > 150) t = t.substring(0, 150);
        if (tooth != null && !validTooth(tooth)) throw ApiException.field("tooth", "Pieza dental inválida (notación FDI).");
        try {
            return store(p, file.getBytes(), type, k, t, clean(note), tooth, share, null);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVO", "No se pudo guardar el archivo.");
        }
    }

    /** Consentimiento, receta o presupuesto con la firma del paciente: se genera un PDF. */
    @Transactional
    public PatientFile document(Long patientId, DocumentRequest r) {
        Patient p = patient(patientId);
        String kind = r.kind() == null ? "" : r.kind().trim().toUpperCase();
        if (!List.of("CONSENTIMIENTO", "RECETA", "PRESUPUESTO").contains(kind)) {
            throw ApiException.field("kind", "Elige consentimiento, receta o presupuesto.");
        }
        if (r.body() == null || r.body().isBlank()) throw ApiException.field("body", "Escribe el contenido del documento.");
        String title = r.title() == null || r.title().isBlank() ? defaultTitle(kind, null) : r.title().trim();
        byte[] signature = null;
        if (r.signatureJpeg() != null && !r.signatureJpeg().isBlank()) {
            String b64 = r.signatureJpeg().contains(",") ? r.signatureJpeg().substring(r.signatureJpeg().indexOf(',') + 1) : r.signatureJpeg();
            try {
                signature = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                throw ApiException.field("signatureJpeg", "La firma no es válida.");
            }
            if (SimplePdf.jpegSize(signature) == null) throw ApiException.field("signatureJpeg", "La firma debe ser una imagen JPEG.");
        }
        if ("CONSENTIMIENTO".equals(kind) && signature == null) {
            throw ApiException.field("signatureJpeg", "El consentimiento necesita la firma del paciente.");
        }
        String now = Dates.now().format(DMY);
        SimplePdf pdf = new SimplePdf()
                .header(clinicName, clinicAddress + " · RUC 10102984841")
                .title(title)
                .paragraph("Paciente: " + p.getFullName() + "    " + p.docType + " " + p.docNumber, 10, false)
                .paragraph("Fecha: " + now + "    Profesional: " + CurrentUser.usernameOrSystem(), 10, false)
                .space(10)
                .paragraph(r.body().trim(), 11, false)
                .space(24);
        if (signature != null) pdf.jpeg(signature, 200);
        pdf.signatureLine("Firma: " + (r.signerName() == null || r.signerName().isBlank() ? p.getFullName() : r.signerName().trim()));
        if ("RECETA".equals(kind)) pdf.space(30).signatureLine("Firma y sello del odontólogo");
        PatientFile f = store(p, pdf.build(), "application/pdf", kind, title, null, null,
                r.shareWithPatient() == null || r.shareWithPatient(), signature == null ? null : Dates.now());
        return f;
    }

    private PatientFile store(Patient p, byte[] bytes, String type, String kind, String title, String note, Integer tooth,
                              boolean share, java.time.LocalDateTime signedAt) {
        String name = UUID.randomUUID().toString().replace("-", "") + TYPES.get(type);
        try {
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVO", "No se pudo guardar el archivo en el servidor.");
        }
        PatientFile f = new PatientFile();
        f.patientId = p.id;
        f.kind = kind;
        f.title = title;
        f.note = note;
        f.tooth = tooth;
        f.contentType = type;
        f.sizeBytes = bytes.length;
        f.storageName = name;
        f.sharedWithPatient = share;
        f.signedAt = signedAt;
        f.createdAt = Dates.now();
        f.createdBy = CurrentUser.usernameOrSystem();
        files.save(f);
        audit.record("HISTORIA", p.id, "ARCHIVO", "Agregó " + kind.toLowerCase() + ": " + title);
        return f;
    }

    public byte[] content(PatientFile f) {
        try {
            return Files.readAllBytes(dir.resolve(f.storageName));
        } catch (IOException e) {
            throw ApiException.notFound("El contenido del archivo");
        }
    }

    @Transactional
    public void delete(Long id) {
        PatientFile f = get(id);
        f.deletedAt = Dates.now();
        f.deletedBy = CurrentUser.usernameOrSystem();
        files.save(f);
        audit.record("HISTORIA", f.patientId, "ARCHIVO_ELIMINADO", f.title);
    }

    @Transactional
    public PatientFile share(Long id, boolean share) {
        PatientFile f = get(id);
        f.sharedWithPatient = share;
        files.save(f);
        return f;
    }

    // ------------------------------------------------------------------ enlaces temporales (para abrir en el teléfono)

    /** Enlace firmado que vence en 10 minutos: el teléfono lo abre en su visor sin enviar el token de sesión. */
    public String link(Long fileId) {
        return signedPath("/api/public/files/", fileId);
    }

    public PatientFile resolve(String token) {
        return get(resolveId(token));
    }

    public String signedPath(String prefix, Long id) {
        long exp = System.currentTimeMillis() / 1000 + 600;
        String payload = id + "." + exp;
        return prefix + payload + "." + sign(prefix + payload);
    }

    /** Valida el enlace firmado (y que no haya vencido) y devuelve el identificador. */
    public Long resolveId(String token) {
        return resolveId("/api/public/files/", token);
    }

    public Long resolveId(String prefix, String token) {
        String[] p = token.split("\\.");
        if (p.length != 3) throw ApiException.notFound("El enlace");
        String payload = p[0] + "." + p[1];
        if (!MessageDigestEquals(sign(prefix + payload), p[2])) throw ApiException.notFound("El enlace");
        if (Long.parseLong(p[1]) < System.currentTimeMillis() / 1000) {
            throw new ApiException(HttpStatus.GONE, "ENLACE_VENCIDO", "El enlace venció. Vuelve a abrirlo desde la app.");
        }
        return Long.parseLong(p[0]);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(linkKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean MessageDigestEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ utilidades

    private static String normalizeKind(String kind, String type) {
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (KINDS.contains(k)) return k;
        return type.startsWith("image/") ? "FOTO" : "DOCUMENTO";
    }

    private static String defaultTitle(String kind, String original) {
        return switch (kind) {
            case "FOTO" -> "Foto intraoral";
            case "RADIOGRAFIA" -> "Radiografía";
            case "CONSENTIMIENTO" -> "Consentimiento informado";
            case "RECETA" -> "Receta";
            case "PRESUPUESTO" -> "Presupuesto";
            default -> original == null || original.isBlank() ? "Documento" : original;
        };
    }

    static boolean validTooth(int t) {
        int q = t / 10, n = t % 10;
        return (q >= 1 && q <= 4 && n >= 1 && n <= 8) || (q >= 5 && q <= 8 && n >= 1 && n <= 5);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : (s.trim().length() > 500 ? s.trim().substring(0, 500) : s.trim());
    }
}
