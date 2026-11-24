package pe.clinicacardenas.inventory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Solicitudes de insumos (odontólogo → recepción), compras a proveedores, lotes y vencimientos. */
@RestController
public class SupplyController {

    private final SupplyRequestService requests;
    private final InventoryPurchaseService purchases;
    private final InventoryService inventory;

    public SupplyController(SupplyRequestService requests, InventoryPurchaseService purchases, InventoryService inventory) {
        this.requests = requests;
        this.purchases = purchases;
        this.inventory = inventory;
    }

    @GetMapping("/api/supply-requests/catalog")
    @PreAuthorize("hasAnyAuthority('SOLICITAR_INSUMOS','INVENTARIO')")
    public List<Map<String, Object>> catalog() { return requests.catalog(); }

    @GetMapping("/api/supply-requests")
    @PreAuthorize("hasAnyAuthority('SOLICITAR_INSUMOS','INVENTARIO')")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mine) {
        // Quien no administra inventario solo ve sus propias solicitudes
        boolean onlyMine = mine || !pe.clinicacardenas.security.PermissionService.has("INVENTARIO");
        return requests.list(status, onlyMine);
    }

    @PostMapping("/api/supply-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SOLICITAR_INSUMOS','INVENTARIO')")
    public Map<String, Object> create(@RequestBody SupplyRequestService.NewRequest r) { return requests.create(r); }

    @PostMapping("/api/supply-requests/{id}/answer")
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public Map<String, Object> answer(@PathVariable Long id, @RequestBody SupplyRequestService.Answer a) { return requests.answer(id, a); }

    @PostMapping("/api/supply-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('SOLICITAR_INSUMOS','INVENTARIO')")
    public Map<String, Object> cancel(@PathVariable Long id) { return requests.cancel(id); }

    @GetMapping("/api/inventory/purchases")
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public List<Map<String, Object>> purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return purchases.list(from, to);
    }

    @GetMapping("/api/inventory/purchases/{id}")
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public Map<String, Object> purchase(@PathVariable Long id) { return purchases.get(id); }

    @PostMapping("/api/inventory/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public Map<String, Object> register(@RequestBody InventoryPurchaseService.PurchaseRequest r) { return purchases.register(r); }

    @PostMapping("/api/inventory/purchases/{id}/pay")
    @PreAuthorize("hasAuthority('CAJA_EGRESOS') or hasRole('ADMIN')")
    public Map<String, Object> pay(@PathVariable Long id, @RequestBody InventoryPurchaseService.PayRequest r) { return purchases.pay(id, r); }

    @GetMapping("/api/inventory/{id}/lots")
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public List<Map<String, Object>> lots(@PathVariable Long id) { return inventory.lots(id); }

    @GetMapping("/api/inventory/expiring")
    @PreAuthorize("hasAuthority('INVENTARIO')")
    public List<Map<String, Object>> expiring() { return inventory.expiring(); }
}
