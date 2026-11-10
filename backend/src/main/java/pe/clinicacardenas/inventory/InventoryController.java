package pe.clinicacardenas.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAuthority('INVENTARIO')")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return service.overview();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItem create(@RequestBody InventoryService.ItemRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public InventoryItem update(@PathVariable Long id, @RequestBody InventoryService.ItemRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/moves")
    public Map<String, Object> move(@PathVariable Long id, @RequestBody InventoryService.MoveRequest req) {
        return service.registerMove(id, req);
    }

    @GetMapping("/{id}/moves")
    public List<InventoryMove> moves(@PathVariable Long id) {
        return service.moves(id);
    }

    @GetMapping("/supplies")
    public List<Map<String, Object>> supplies() {
        return service.supplies();
    }

    @PutMapping("/supplies/{procedureId}")
    public List<Map<String, Object>> saveSupplies(@PathVariable Long procedureId,
                                                  @RequestBody List<InventoryService.SupplyRequest> list) {
        return service.saveSupplies(procedureId, list);
    }
}
