package pe.clinica.cardenas.backupservice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/backups")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {
    private final BackupOrchestrator backup;
    public BackupController(BackupOrchestrator backup){this.backup=backup;}

    @GetMapping("/catalog") public List<BackupOrchestrator.ServiceCatalog> catalog(HttpServletRequest r){return backup.catalog(auth(r));}
    @GetMapping("/reports") public List<BackupOrchestrator.ReportInfo> reports(){return backup.reports();}

    @GetMapping("/tables/{service}/{table}/excel")
    public ResponseEntity<byte[]> table(@PathVariable String service,@PathVariable String table,HttpServletRequest r){return file(backup.exportTable(service,table,auth(r),false),"tabla-"+service+"-"+table.toLowerCase()+".xlsx",MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));}

    @GetMapping("/tables/{service}/{table}/template")
    public ResponseEntity<byte[]> template(@PathVariable String service,@PathVariable String table,HttpServletRequest r){return file(backup.exportTable(service,table,auth(r),true),"plantilla-"+service+"-"+table.toLowerCase()+".xlsx",MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));}

    @PostMapping(value="/tables/{service}/{table}/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BackupOrchestrator.ImportResponse importExcel(@PathVariable String service,@PathVariable String table,@RequestParam(defaultValue="APPEND") String mode,@RequestPart("file") MultipartFile file,HttpServletRequest r){return backup.importExcel(service,table,mode,file,auth(r));}

    @GetMapping("/reports/{id}/excel")
    public ResponseEntity<byte[]> report(@PathVariable String id,HttpServletRequest r){return file(backup.exportReport(id,auth(r)),"informe-"+id+".xlsx",MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));}

    @GetMapping("/global")
    public ResponseEntity<byte[]> global(HttpServletRequest r){String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));return file(backup.globalZip(auth(r)),"respaldo-clinica-cardenas-"+stamp+".zip",MediaType.parseMediaType("application/zip"));}

    private String auth(HttpServletRequest r){return r.getHeader(HttpHeaders.AUTHORIZATION);}
    private ResponseEntity<byte[]> file(byte[] bytes,String filename,MediaType type){return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").contentLength(bytes.length).body(bytes);}
}
