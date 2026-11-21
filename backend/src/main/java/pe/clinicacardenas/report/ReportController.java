package pe.clinicacardenas.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** HU-19, HU-20 y HU-21: indicadores gerenciales para la dirección. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAuthority('REPORTES')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/income")
    public Map<String, Object> income(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String group) {
        return service.income(from, to, group);
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) Long dentistId,
                                            @RequestParam(required = false) String group) {
        return service.appointments(from, to, dentistId, group);
    }

    @GetMapping("/treatments")
    public Map<String, Object> treatments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) Long dentistId) {
        return service.treatments(from, to, dentistId);
    }

    // ---------- v2.8: reporte anual descargable
    @org.springframework.beans.factory.annotation.Autowired
    private AnnualReportService annual;

    @GetMapping("/annual")
    public Map<String, Object> annual(@RequestParam int year) { return annual.report(year); }

    @GetMapping("/annual/pdf")
    public org.springframework.http.ResponseEntity<byte[]> annualPdf(@RequestParam int year) {
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"reporte-anual-" + year + ".pdf\"").body(annual.pdf(year));
    }

    @GetMapping("/annual/csv")
    public org.springframework.http.ResponseEntity<byte[]> annualCsv(@RequestParam int year) {
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"reporte-anual-" + year + ".csv\"")
                .body(annual.csv(year).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
