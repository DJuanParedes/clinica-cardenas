package pe.clinicacardenas.files;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Sirve las fotos de productos y odontólogos (nombre aleatorio; nunca datos de pacientes). */
@RestController
public class PublicImageController {

    private final ImageService images;

    public PublicImageController(ImageService images) {
        this.images = images;
    }

    @GetMapping("/api/public/images/{kind}/{name}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String kind, @PathVariable String name) {
        Path p = images.file(kind, name);
        if (p == null) return ResponseEntity.notFound().build();
        MediaType type = MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) type = MediaType.IMAGE_PNG;
        else if (name.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(p));
    }
}
