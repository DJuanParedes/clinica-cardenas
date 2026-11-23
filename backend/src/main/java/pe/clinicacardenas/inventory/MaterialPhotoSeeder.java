package pe.clinicacardenas.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pe.clinicacardenas.files.ImageService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.10: fotos reales para los materiales de ejemplo. Al arrancar, a cada material sin foto cuyo nombre empieza como
 * una de estas claves se le descarga su foto de Wikimedia Commons (licencias libres; créditos en
 * docs/CREDITOS_IMAGENES.md). Si no hay internet, no pasa nada: se intenta en el siguiente arranque. La clínica puede
 * cambiar cualquier foto desde Inventario → Ver material.
 */
@Component
public class MaterialPhotoSeeder {

    private static final Logger log = LoggerFactory.getLogger(MaterialPhotoSeeder.class);

    static final Map<String, String> PHOTOS = new LinkedHashMap<>();
    static {
        PHOTOS.put("Anestesia lidocaína", "Zylinderampulle.JPG");
        PHOTOS.put("Guantes de nitrilo", "Disposable nitrile glove.jpg");
        PHOTOS.put("Mascarilla", "A surgical mask (2017).jpg");
        PHOTOS.put("Resina compuesta", "Tetric Ceram Composite 20090914 02.JPG");
        PHOTOS.put("Adhesivo", "Dental bonding agents.jpg");
        PHOTOS.put("Pasta profiláctica", "Tooth polishing 9332.JPG");
        PHOTOS.put("Flúor", "Fluoridové přípravky.jpg");
        PHOTOS.put("Limas endodónticas", "Endodontic Files 20-40.JPG");
        PHOTOS.put("Gutapercha", "Stifte Guttapercha.jpg");
        PHOTOS.put("Sutura", "Surgical suture needles 02.JPG");
        PHOTOS.put("Película radiográfica", "Dental x-ray film 02.JPG");
    }

    private final JdbcTemplate jdbc;
    private final ImageService images;
    private final boolean enabled;

    public MaterialPhotoSeeder(JdbcTemplate jdbc, ImageService images, @Value("${app.seed.material-photos:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.images = images;
        this.enabled = enabled;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) return;
        List<Map<String, Object>> items = jdbc.queryForList("select id, name from inventory_items where image_path is null and active = true");
        if (items.isEmpty()) return;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        int done = 0;
        for (Map<String, Object> it : items) {
            String name = String.valueOf(it.get("name"));
            String file = PHOTOS.entrySet().stream().filter(e -> name.startsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
            if (file == null) continue;
            try {
                String url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file, StandardCharsets.UTF_8).replace("+", "%20") + "?width=480";
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "ClinicaCardenas/2.10 (sistema de gestión de clínica dental; admin@clinicacardenas.pe)").build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) continue;
                String path = images.saveBytes("productos", r.body());
                if (path == null) continue;
                jdbc.update("update inventory_items set image_path = ? where id = ? and image_path is null", path, it.get("id"));
                done++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.info("Fotos de materiales: sin conexión con Wikimedia ({}); se intentará en el próximo arranque.", e.getClass().getSimpleName());
                return;
            }
        }
        if (done > 0) log.info("Fotos de materiales: {} fotos agregadas", done);
    }
}
