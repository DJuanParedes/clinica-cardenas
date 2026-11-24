package pe.clinicacardenas.agenda;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.files.ImageService;

/** Foto de cada odontólogo: se ve en la agenda, en usuarios y en la app del paciente al reservar. */
@RestController
@RequestMapping("/api/dentists/{id}/photo")
@PreAuthorize("hasAuthority('USUARIOS')")
public class DentistPhotoController {

    private final DentistRepository dentists;
    private final ImageService images;
    private final AuditService audit;

    public DentistPhotoController(DentistRepository dentists, ImageService images, AuditService audit) {
        this.dentists = dentists;
        this.images = images;
        this.audit = audit;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public Dentist upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Dentist d = dentists.findById(id).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        String old = d.photoPath;
        d.photoPath = images.save("doctores", file);
        dentists.save(d);
        images.delete(old);
        audit.register("ODONTOLOGO", d.id, "FOTO", d.getFullName());
        return d;
    }

    @DeleteMapping
    @Transactional
    public Dentist remove(@PathVariable Long id) {
        Dentist d = dentists.findById(id).orElseThrow(() -> ApiException.notFound("El odontólogo"));
        images.delete(d.photoPath);
        d.photoPath = null;
        dentists.save(d);
        return d;
    }
}
