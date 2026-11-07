package pe.clinicacardenas.files;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Abre un archivo con un enlace temporal firmado (lo usa la app del paciente para verlo en el teléfono). */
@RestController
public class PublicFileController {

    private final FileService service;

    public PublicFileController(FileService service) {
        this.service = service;
    }

    @GetMapping("/api/public/files/{token}")
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        return FileController.serve(service, service.resolve(token));
    }
}
