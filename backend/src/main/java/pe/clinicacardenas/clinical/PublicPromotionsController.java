package pe.clinicacardenas.clinical;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Promociones vigentes para el portal y la app del paciente (solo lo que se publica: nombre, precio y condiciones). */
@RestController
public class PublicPromotionsController {

    private final PromotionService service;

    public PublicPromotionsController(PromotionService service) {
        this.service = service;
    }

    @GetMapping("/api/public/promotions")
    public List<Map<String, Object>> current() {
        return service.promotions().stream().filter(p -> "VIGENTE".equals(p.get("state"))).map(p -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", p.get("id"));
            m.put("name", p.get("name"));
            m.put("procedure", p.get("procedureName") != null ? p.get("procedureName") : p.get("family"));
            m.put("listPrice", p.get("listPrice"));
            m.put("finalPrice", p.get("finalPrice"));
            m.put("discountType", p.get("discountType"));
            m.put("value", p.get("value"));
            m.put("validTo", p.get("validTo"));
            m.put("conditions", p.get("conditions"));
            return m;
        }).toList();
    }
}
