package pe.clinicacardenas.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v2.11: eliminar cualquier registro y consultar la papelera. Solo con el permiso ELIMINAR_REGISTROS (administrador). */
@RestController
@RequestMapping("/api/admin/records")
@PreAuthorize("hasAuthority('ELIMINAR_REGISTROS')")
public class DeletionController {

    private final DeletionService service;

    public DeletionController(DeletionService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public List<Map<String, Object>> types() {
        return service.types();
    }

    @GetMapping("/{entity}/{id}/preview")
    public Map<String, Object> preview(@PathVariable String entity, @PathVariable String id) {
        return service.preview(entity, id);
    }

    @PostMapping("/{entity}/{id}/delete")
    public Map<String, Object> delete(@PathVariable String entity, @PathVariable String id, @RequestBody DeletionService.Confirm c) {
        return service.delete(entity, id, c);
    }

    @GetMapping("/trash")
    public List<Map<String, Object>> trash() {
        return service.trash();
    }

    @GetMapping("/trash/{id}")
    public ResponseEntity<String> trashData(@PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eliminado-" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.trashData(id));
    }
}
