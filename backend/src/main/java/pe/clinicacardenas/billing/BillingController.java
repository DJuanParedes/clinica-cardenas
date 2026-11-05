package pe.clinicacardenas.billing;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HU-10, HU-11 y HU-12. */
@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasAnyRole('CAJA','ADMIN')")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @GetMapping("/series")
    public List<InvoiceSeries> series() {
        return service.activeSeries();
    }

    @GetMapping("/company")
    public UblBuilder.Company company() {
        return service.company();
    }

    @GetMapping("/patients/{patientId}/billable-items")
    public List<Map<String, Object>> billable(@PathVariable Long patientId) {
        return service.billableItems(patientId);
    }

    @GetMapping("/patients/{patientId}/invoices")
    public List<Invoice> byPatient(@PathVariable Long patientId) {
        return service.byPatient(patientId);
    }

    @GetMapping("/invoices")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String docType,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "0") int page) {
        return service.search(q, status, docType, from, to, page);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody BillingService.InvoiceRequest req) {
        return service.create(req);
    }

    @GetMapping("/invoices/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/invoices/{id}/send")
    public Map<String, Object> send(@PathVariable Long id) {
        return service.send(id);
    }

    @PostMapping("/invoices/{id}/status")
    public Map<String, Object> checkStatus(@PathVariable Long id) {
        return service.checkStatus(id);
    }

    @GetMapping("/ruc/{ruc}")
    public Map<String, Object> ruc(@PathVariable String ruc) {
        return service.lookupRuc(ruc);
    }

    @GetMapping("/invoices/{id}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        Invoice inv = service.get(id);
        return file(company() .ruc() + "-" + inv.docType + "-" + inv.series + "-" + inv.number + ".xml", inv.xmlContent);
    }

    @GetMapping("/invoices/{id}/cdr")
    public ResponseEntity<byte[]> cdr(@PathVariable Long id) {
        Invoice inv = service.get(id);
        if (inv.cdrContent == null) {
            throw ApiException.notFound("La constancia de recepción (CDR)");
        }
        return file("R-" + company().ruc() + "-" + inv.docType + "-" + inv.series + "-" + inv.number + ".xml", inv.cdrContent);
    }

    private static ResponseEntity<byte[]> file(String name, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
