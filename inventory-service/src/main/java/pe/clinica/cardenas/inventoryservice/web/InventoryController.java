package pe.clinica.cardenas.inventoryservice.web;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.inventoryservice.domain.InventoryItem;
import pe.clinica.cardenas.inventoryservice.domain.StockMovement;
import pe.clinica.cardenas.inventoryservice.repository.InventoryItemRepository;
import pe.clinica.cardenas.inventoryservice.repository.StockMovementRepository;
import pe.clinica.cardenas.inventoryservice.web.dto.InventoryItemRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<InventoryItem> items() { return items.findAllByOrderByNameAsc(); }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public InventoryItem create(@Valid @RequestBody InventoryItemRequest body) {
        InventoryItem item = InventoryItem.builder()
                .code(body.code().trim()).name(body.name().trim()).category(body.category()).unit(body.unit().trim())
                .currentStock(body.currentStock()).minimumStock(body.minimumStock()).lot(body.lot()).expiryDate(body.expiryDate())
                .unitCost(body.unitCost()).inventoryStatus(body.inventoryStatus()).active(true).build();
        updateStatus(item); return items.save(item);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public InventoryItem update(@PathVariable Long id, @Valid @RequestBody InventoryItemRequest incoming) {
        InventoryItem item = find(id);
        item.setCode(incoming.code().trim()); item.setName(incoming.name().trim()); item.setCategory(incoming.category());
        item.setUnit(incoming.unit().trim()); item.setMinimumStock(incoming.minimumStock()); item.setLot(incoming.lot());
        item.setExpiryDate(incoming.expiryDate()); item.setUnitCost(incoming.unitCost());
        if (incoming.active() != null) item.setActive(incoming.active());
        updateStatus(item); return items.save(item);
    }

    @PatchMapping("/items/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public InventoryItem active(@PathVariable Long id, @RequestParam boolean value) {
        InventoryItem item = find(id); item.setActive(value); updateStatus(item); return items.save(item);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<StockMovement> movements(@RequestParam(required = false) Long itemId) {
        return itemId == null ? movements.findTop200ByOrderByCreatedAtDesc() : movements.findAllByItemIdOrderByCreatedAtDesc(itemId);
    }

    @PostMapping("/movements")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public StockMovement movement(@Valid @RequestBody StockMovement movement, Authentication authentication) {
        InventoryItem item = find(movement.getItemId());
        BigDecimal delta = switch (movement.getMovementType()) {
            case ENTRADA, AJUSTE_POSITIVO -> movement.getQuantity();
            case SALIDA, AJUSTE_NEGATIVO -> movement.getQuantity().negate();
        };
        BigDecimal newStock = item.getCurrentStock().add(delta);
        if (newStock.signum() < 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "El movimiento dejaría el stock en negativo");
        item.setCurrentStock(newStock); updateStatus(item); items.save(item);
        movement.setId(null); movement.setItemCode(item.getCode()); movement.setItemName(item.getName());
        movement.setResponsible(authentication.getName()); movement.setCreatedAt(LocalDateTime.now());
        return movements.save(movement);
    }

    private InventoryItem find(Long id) {
        return items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insumo no encontrado"));
    }

    private void updateStatus(InventoryItem item) {
        if (!item.isActive()) { item.setInventoryStatus("BLOQUEADO"); return; }
        if (item.getExpiryDate() != null && item.getExpiryDate().isBefore(LocalDate.now())) { item.setInventoryStatus("VENCIDO"); return; }
        if (item.getCurrentStock() == null) item.setCurrentStock(BigDecimal.ZERO);
        if (item.getMinimumStock() == null) item.setMinimumStock(BigDecimal.ZERO);
        if (item.getCurrentStock().signum() == 0) item.setInventoryStatus("AGOTADO");
        else if (item.getCurrentStock().compareTo(item.getMinimumStock()) <= 0) item.setInventoryStatus("STOCK BAJO");
        else item.setInventoryStatus("DISPONIBLE");
    }
}
