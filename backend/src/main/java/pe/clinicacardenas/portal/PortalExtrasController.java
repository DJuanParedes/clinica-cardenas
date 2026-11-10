package pe.clinicacardenas.portal;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.files.FileService;

import java.time.LocalDate;
import java.util.Map;

/** App del paciente: reservas, avisos, pagos en línea, documentos y encuesta. */
@RestController
public class PortalExtrasController {

    private final PortalExtrasService service;
    private final FileService files;

    public PortalExtrasController(PortalExtrasService service, FileService files) {
        this.service = service;
        this.files = files;
    }

    @GetMapping("/api/portal/booking/options")
    public Map<String, Object> options() {
        return service.bookingOptions();
    }

    @GetMapping("/api/portal/booking/slots")
    public Map<String, Object> slots(@RequestParam Long dentistId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "EVALUACION") String reasonCode,
                                     @RequestParam(required = false) Long excludeId) {
        return service.slots(dentistId, date, reasonCode, excludeId);
    }

    @PostMapping("/api/portal/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> book(Authentication auth, @RequestBody PortalExtrasService.BookRequest req) {
        return service.book(auth.getName(), req);
    }

    @PostMapping("/api/portal/appointments/{id}/reschedule")
    public Map<String, Object> reschedule(Authentication auth, @PathVariable Long id,
                                          @RequestBody PortalExtrasService.RescheduleRequest req) {
        return service.reschedule(auth.getName(), id, req);
    }

    @PostMapping("/api/portal/appointments/{id}/cancel")
    public Map<String, Object> cancel(Authentication auth, @PathVariable Long id,
                                      @RequestBody(required = false) PortalExtrasService.CancelRequest req) {
        return service.cancel(auth.getName(), id, req);
    }

    @GetMapping("/api/portal/notices")
    public Map<String, Object> notices(Authentication auth) {
        return service.notices(auth.getName());
    }

    @PostMapping("/api/portal/notices/read")
    public Map<String, Object> read(Authentication auth) {
        return service.markRead(auth.getName());
    }

    @PostMapping("/api/portal/payments")
    public Map<String, Object> pay(Authentication auth, @RequestBody PortalExtrasService.PayRequest req) {
        return service.pay(auth.getName(), req);
    }

    @GetMapping("/api/portal/documents")
    public Map<String, Object> documents(Authentication auth) {
        return service.documents(auth.getName());
    }

    @GetMapping("/api/portal/documents/files/{id}/link")
    public Map<String, String> fileLink(Authentication auth, @PathVariable Long id) {
        return service.fileLink(auth.getName(), id);
    }

    @GetMapping("/api/portal/documents/invoices/{id}/link")
    public Map<String, String> invoiceLink(Authentication auth, @PathVariable Long id) {
        return service.invoiceLink(auth.getName(), id);
    }

    @GetMapping("/api/portal/surveys/pending")
    public Map<String, Object> pendingSurvey(Authentication auth) {
        return service.pendingSurvey(auth.getName());
    }

    @PostMapping("/api/portal/surveys")
    public Map<String, Object> rate(Authentication auth, @RequestBody PortalExtrasService.SurveyRequest req) {
        return service.rate(auth.getName(), req);
    }

    /** Boleta o factura en PDF con enlace temporal firmado (se abre en el visor del teléfono). */
    @GetMapping("/api/public/invoices/{token}")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable String token) {
        Long id = files.resolveId("/api/public/invoices/", token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprobante-" + id + ".pdf\"")
                .body(service.invoicePdf(id));
    }
}
