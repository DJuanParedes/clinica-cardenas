package pe.clinica.cardenas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/data-transfer")
public class DataTransferController {
    private final DataTransferService service;
    private final boolean importEnabled;

    public DataTransferController(DataTransferService service,
                                  @Value("${spring.application.name:}") String applicationName) {
        this.service = service;
        // Las cuentas de acceso solo nacen por Usuarios y roles. Aunque este endpoint
        // no se enruta por Gateway en producción, auth-service rechaza la importación
        // también en la capa interna para evitar saltarse DNI/teléfono/OTP/auditoría.
        this.importEnabled = !"auth-service".equalsIgnoreCase(applicationName == null ? "" : applicationName.trim());
    }

    @GetMapping("/tables")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DataTransferService.TableInfo> tables(){ return service.tables(); }

    @GetMapping("/tables/{table}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> rows(@PathVariable String table){ return service.rows(table); }

    @PostMapping("/tables/{table}/import")
    @PreAuthorize("hasRole('ADMIN')")
    public DataTransferService.ImportResult importRows(@PathVariable String table,
                                                        @RequestParam(defaultValue="APPEND") String mode,
                                                        @RequestBody List<Map<String,Object>> rows){
        if (!importEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Las cuentas de usuario no se importan por Excel; créalas desde Usuarios y roles");
        }
        return service.importRows(table, mode, rows);
    }
}
