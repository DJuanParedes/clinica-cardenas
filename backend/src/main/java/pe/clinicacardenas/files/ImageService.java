package pe.clinicacardenas.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fotos de productos del inventario y de los odontólogos. Se guardan en la carpeta de archivos con un nombre
 * aleatorio (no adivinable) y se sirven por /api/public/images/{tipo}/{archivo} para usarlas en etiquetas img.
 * Solo se aceptan JPG, PNG o WEBP reales (se revisa la firma del archivo, no solo la extensión), hasta 4 MB.
 */
@Service
public class ImageService {

    public static final Pattern NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png|webp)$");
    private static final long MAX = 4L * 1024 * 1024;

    private final Path root;

    public ImageService(@Value("${app.files.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().resolve("imagenes");
        Files.createDirectories(root.resolve("productos"));
        Files.createDirectories(root.resolve("doctores"));
    }

    /** Guarda la imagen y devuelve "tipo/archivo". */
    public String save(String kind, MultipartFile file) {
        checkKind(kind);
        if (file == null || file.isEmpty()) throw ApiException.field("file", "Elige una imagen.");
        if (file.getSize() > MAX) throw ApiException.field("file", "La imagen pesa más de 4 MB. Usa una más liviana.");
        try {
            byte[] head = new byte[12];
            int n;
            try (var in = file.getInputStream()) { n = in.readNBytes(head, 0, 12); }
            String ext = extension(head, n);
            if (ext == null) throw ApiException.field("file", "Solo se aceptan imágenes JPG, PNG o WEBP.");
            String name = UUID.randomUUID() + "." + ext;
            try (var in = file.getInputStream()) {
                Files.copy(in, root.resolve(kind).resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return kind + "/" + name;
        } catch (IOException e) {
            throw ApiException.badRequest("IMAGEN_NO_GUARDADA", "No se pudo guardar la imagen.");
        }
    }

    /** v2.10: guarda una imagen que ya está en memoria (fotos de ejemplo descargadas por el servidor). */
    public String saveBytes(String kind, byte[] data) {
        checkKind(kind);
        if (data == null || data.length == 0 || data.length > MAX) return null;
        String ext = extension(data, Math.min(12, data.length));
        if (ext == null) return null;
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.write(root.resolve(kind).resolve(name), data);
            return kind + "/" + name;
        } catch (IOException e) {
            return null;
        }
    }

    public void delete(String path) {
        if (path == null || !path.contains("/")) return;
        String[] parts = path.split("/", 2);
        if (!isKind(parts[0]) || !NAME.matcher(parts[1]).matches()) return;
        try { Files.deleteIfExists(root.resolve(parts[0]).resolve(parts[1])); } catch (IOException ignored) { /* ya no está */ }
    }

    /** Archivo a servir, o null si el nombre no es válido o no existe. */
    public Path file(String kind, String name) {
        if (!isKind(kind) || name == null || !NAME.matcher(name).matches()) return null;
        Path p = root.resolve(kind).resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    public static String url(String path) {
        return path == null || path.isBlank() ? null : "/api/public/images/" + path;
    }

    private static boolean isKind(String k) {
        return "productos".equals(k) || "doctores".equals(k);
    }

    private static void checkKind(String k) {
        if (!isKind(k)) throw ApiException.badRequest("TIPO_INVALIDO", "Tipo de imagen no válido.");
    }

    static String extension(byte[] h, int n) {
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
