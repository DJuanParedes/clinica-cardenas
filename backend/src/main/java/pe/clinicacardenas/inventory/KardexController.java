package pe.clinicacardenas.inventory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAuthority('INVENTARIO_COSTOS')")
public class KardexController {

    private final KardexService service;

    public KardexController(KardexService service) {
        this.service = service;
    }

    @GetMapping("/{id}/kardex")
    public Map<String, Object> kardex(@PathVariable Long id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.kardex(id, from, to);
    }

    @GetMapping("/valuation")
    public Map<String, Object> valuation() { return service.valuation(); }

    @GetMapping("/consumption")
    public Map<String, Object> consumption(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Long patientId) {
        return service.consumption(from, to, patientId);
    }
}
