package pe.clinica.cardenas.contentservice.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    @GetMapping("/home")
    public Map<String, Object> home() {
        return Map.of(
                "clinic", "Clínica Cárdenas",
                "tagline", "Atención odontológica en La Floresta",
                "specialties", List.of(
                        "Odontología general", "Ortodoncia", "Implantología", "Endodoncia",
                        "Periodoncia", "Odontopediatría", "Cirugía oral", "Rehabilitación oral",
                        "Estética dental", "Radiología digital"
                ),
                "hours", "Atención previa coordinación",
                "phone", "",
                "address", "La Floresta, Lima, Perú"
        );
    }
}
