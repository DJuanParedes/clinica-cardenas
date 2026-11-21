package pe.clinicacardenas.messaging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** v2.6: enlaces públicos (sin iniciar sesión): reserva de citas, prerregistro y encuesta. */
@RestController
@RequestMapping("/api/public")
public class PublicPatientController {

    private final PublicPatientService service;

    public PublicPatientController(PublicPatientService service) {
        this.service = service;
    }

    @GetMapping("/booking/options")
    public Map<String, Object> options() {
        return service.bookingOptions();
    }

    @GetMapping("/booking/slots")
    public Map<String, Object> slots(@RequestParam Long dentistId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "EVALUACION") String reasonCode) {
        return service.slots(dentistId, date, reasonCode);
    }

    @PostMapping("/booking")
    public Map<String, Object> book(@RequestBody PublicPatientService.BookingRequest body, HttpServletRequest req) {
        return service.book(body, ip(req));
    }

    @GetMapping("/preregistro")
    public Map<String, Object> form() {
        return service.preregisterForm();
    }

    @PostMapping("/preregistro")
    public Map<String, Object> preregister(@RequestBody PublicPatientService.PreregisterRequest body, HttpServletRequest req) {
        return service.preregister(body, ip(req));
    }

    @GetMapping("/survey/{token}")
    public Map<String, Object> survey(@PathVariable String token) {
        return service.survey(token);
    }

    @PostMapping("/survey/{token}")
    public Map<String, Object> rate(@PathVariable String token, @RequestBody PublicPatientService.SurveyRequest body) {
        return service.rate(token, body);
    }

    /** Caddy pasa la IP real del visitante en X-Forwarded-For. */
    static String ip(HttpServletRequest req) {
        String f = req.getHeader("X-Forwarded-For");
        String ip = f == null || f.isBlank() ? req.getRemoteAddr() : f.split(",")[0].trim();
        return ip == null ? "desconocida" : ip.substring(0, Math.min(45, ip.length()));
    }
}
