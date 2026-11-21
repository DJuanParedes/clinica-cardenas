package pe.clinicacardenas.complaint;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ComplaintController {

    private final ComplaintService service;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    // ---------- público

    @GetMapping("/public/complaints/info")
    public Map<String, Object> info() {
        return service.info();
    }

    @PostMapping("/public/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> file(@RequestBody ComplaintService.FileRequest r, HttpServletRequest req) {
        String ip = ip(req);
        limit(ip, 6);
        return service.file(r, ip, req.getHeader("User-Agent"));
    }

    @GetMapping("/public/complaints/{code}")
    public Map<String, Object> lookup(@PathVariable String code, @RequestParam String doc, HttpServletRequest req) {
        limit(ip(req) + ":consulta", 30);
        return service.lookup(code, doc);
    }

    @GetMapping("/public/complaints/{code}/pdf")
    public ResponseEntity<byte[]> publicPdf(@PathVariable String code, @RequestParam String doc, HttpServletRequest req) {
        limit(ip(req) + ":consulta", 30);
        Map<String, Object> c = service.byCode(code, doc);
        return pdf(c);
    }

    // ---------- interno

    @GetMapping("/complaints")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status, @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) String q) {
        return service.list(status, year, q);
    }

    @GetMapping("/complaints/summary")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @PutMapping("/complaints/settings")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public Map<String, Object> settings(@RequestBody Map<String, String> body) {
        return service.settings(body);
    }

    @GetMapping("/complaints/{id}")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/complaints/{id}")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody ComplaintService.UpdateRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/complaints/{id}/respond")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public Map<String, Object> respond(@PathVariable Long id, @RequestBody ComplaintService.RespondRequest r) {
        return service.respond(id, r);
    }

    @GetMapping("/complaints/{id}/pdf")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return pdf(service.get(id));
    }

    @GetMapping("/complaints/export")
    @PreAuthorize("hasAuthority('RECLAMOS')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer year) {
        int y = year == null ? Dates.today().getYear() : year;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"libro-reclamaciones-" + y + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.csv(y).getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> pdf(Map<String, Object> c) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"hoja-reclamacion-" + c.get("number") + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.pdf(c));
    }

    private static String ip(HttpServletRequest req) {
        String f = req.getHeader("X-Forwarded-For");
        return f != null && !f.isBlank() ? f.split(",")[0].trim() : req.getRemoteAddr();
    }

    private void limit(String key, int perHour) {
        long now = System.currentTimeMillis();
        Deque<Long> d = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            while (!d.isEmpty() && now - d.peekFirst() > 3_600_000) d.pollFirst();
            if (d.size() >= perHour)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", "Demasiados intentos desde esta conexión. Intenta en una hora.");
            d.addLast(now);
        }
    }
}
