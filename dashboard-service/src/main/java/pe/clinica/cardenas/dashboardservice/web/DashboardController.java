package pe.clinica.cardenas.dashboardservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.clinica.cardenas.dashboardservice.service.RemoteApiClient;

import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
public class DashboardController {
    private final RemoteApiClient api;

    @Value("${services.patient:http://localhost:8082}") String patient;
    @Value("${services.appointment:http://localhost:8083}") String appointment;
    @Value("${services.clinical:http://localhost:8084}") String clinical;
    @Value("${services.finance:http://localhost:8085}") String finance;
    @Value("${services.lab:http://localhost:8086}") String lab;
    @Value("${services.operations:http://localhost:8087}") String operations;
    @Value("${services.content:http://localhost:8089}") String content;
    @Value("${services.inventory:http://localhost:8091}") String inventory;
    @Value("${services.encounter:http://localhost:8092}") String encounter;
    @Value("${services.survey:http://localhost:8093}") String survey;

    @GetMapping("/api/dashboard")
    public Map<String,Object> dashboard(Authentication authentication,
                                        @RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String auth) {
        String role = role(authentication);
        var data = new LinkedHashMap<String,Object>();
        data.put("role", role);
        data.put("patients", api.count(patient + "/api/patients", auth));
        data.put("appointments", api.count(appointment + "/api/appointments", auth));
        data.put("emergencies", "RECEPCION".equals(role) ? 0 : api.count(operations + "/api/emergencies", auth));
        data.put("laboratoryOrders", api.count(lab + "/api/lab-orders", auth));
        data.put("budgets", "RECEPCION".equals(role) ? 0 : api.count(clinical + "/api/budgets", auth));
        data.put("followUps", "RECEPCION".equals(role) ? 0 : api.count(operations + "/api/operations/follow-ups", auth));
        data.put("complaints", api.count(operations + "/api/operations/complaints", auth));
        data.put("equipment", api.count(operations + "/api/operations/equipment", auth));
        data.put("memberships", "RECEPCION".equals(role) ? 0 : api.count(finance + "/api/finance/memberships", auth));
        data.put("commissionTotal", api.sum(finance + "/api/finance/commissions", "finalAmount", auth));
        data.put("webInquiries", api.count(content + "/api/inquiries", auth));
        data.put("inventoryItems", api.count(inventory + "/api/inventory/items", auth));
        data.put("inventoryAlerts", api.countMatching(inventory + "/api/inventory/items", "inventoryStatus", Set.of("STOCK BAJO", "AGOTADO", "VENCIDO"), auth));
        data.put("encounters", "RECEPCION".equals(role) ? 0 : api.count(encounter + "/api/encounters", auth));
        Map<String,Object> quality = api.map(encounter + "/api/encounters/quality/summary", auth);
        Map<String,Object> satisfaction = api.map(survey + "/api/surveys/summary", auth);
        data.put("averageWaitMinutes", quality.getOrDefault("averageWaitMinutes", 0));
        data.put("followUpRate", quality.getOrDefault("followUpRate", 0));
        data.put("satisfactionAverage", satisfaction.getOrDefault("average", 0));
        data.put("satisfactionAlerts", satisfaction.getOrDefault("alerts", 0));
        if (Set.of("ADMIN", "FINANZAS").contains(role)) {
            Map<String,Object> financeSummary = api.map(finance + "/api/finance/summary", auth);
            data.put("paymentTotal", financeSummary.getOrDefault("paymentTotal", 0));
            data.put("expenseTotal", financeSummary.getOrDefault("expenseTotal", 0));
            data.put("openCash", financeSummary.getOrDefault("openCash", 0));
        }
        data.put("message", switch(role) {
            case "ADMIN" -> "Control general de la clínica, seguridad, operaciones y rendimiento.";
            case "DOCTOR" -> "Agenda clínica, historias, presupuestos, laboratorio y seguimientos.";
            case "RECEPCION" -> "Atención integral del paciente, agenda, llegada y coordinación.";
            case "FINANZAS" -> "Presupuestos, comisiones, membresías y control financiero.";
            default -> "Panel de Clínica Cárdenas";
        });
        return data;
    }

    @GetMapping("/api/reports/summary")
    public Map<String,Object> report(Authentication authentication,
                                     @RequestHeader(value=HttpHeaders.AUTHORIZATION,required=false) String auth) {
        String role = role(authentication);
        List<String> labels;
        List<Long> values;
        if ("FINANZAS".equals(role)) {
            labels = List.of("Presupuestos", "Membresías", "Laboratorio", "Comisiones");
            values = List.of(api.count(clinical + "/api/budgets", auth),
                    api.count(finance + "/api/finance/memberships", auth),
                    api.count(lab + "/api/lab-orders", auth),
                    api.count(finance + "/api/finance/commissions", auth));
        } else if ("DOCTOR".equals(role)) {
            labels = List.of("Pacientes", "Citas", "Emergencias", "Laboratorio", "Presupuestos");
            values = List.of(api.count(patient + "/api/patients", auth),
                    api.count(appointment + "/api/appointments", auth),
                    api.count(operations + "/api/emergencies", auth),
                    api.count(lab + "/api/lab-orders", auth),
                    api.count(clinical + "/api/budgets", auth));
        } else if ("RECEPCION".equals(role)) {
            labels = List.of("Pacientes", "Citas hoy", "Solicitudes web", "Reclamos", "Alertas inventario");
            values = List.of(api.count(patient + "/api/patients", auth),
                    countToday(api.list(appointment + "/api/appointments", auth), "startAt"),
                    api.count(content + "/api/inquiries", auth),
                    api.count(operations + "/api/operations/complaints", auth),
                    api.countMatching(inventory + "/api/inventory/items", "inventoryStatus", Set.of("STOCK BAJO", "AGOTADO", "VENCIDO"), auth));
        } else {
            labels = List.of("Pacientes", "Citas", "Laboratorio", "Presupuestos", "Membresías");
            values = List.of(api.count(patient + "/api/patients", auth),
                    api.count(appointment + "/api/appointments", auth),
                    api.count(lab + "/api/lab-orders", auth),
                    api.count(clinical + "/api/budgets", auth),
                    api.count(finance + "/api/finance/memberships", auth));
        }
        Map<String,Object> financeSummary = Set.of("ADMIN", "FINANZAS").contains(role)
                ? api.map(finance + "/api/finance/summary", auth) : Map.of();
        Object monthlyRevenue = financeSummary.getOrDefault("monthlyRevenue", List.of(0,0,0,0,0,0));
        Object months = financeSummary.getOrDefault("months", List.of("Mar","Abr","May","Jun","Jul","Ago"));
        return Map.of("labels", labels, "values", values, "monthlyRevenue", monthlyRevenue, "months", months);
    }

    private long countToday(List<Map<String,Object>> items, String field) {
        String today = LocalDate.now(ZoneId.of("America/Lima")).toString();
        Set<String> inactive = Set.of("CANCELADO", "NO_ASISTIO", "REPROGRAMADA");
        return items.stream()
                .filter(item -> !inactive.contains(Objects.toString(item.get("status"), "").toUpperCase(Locale.ROOT)))
                .map(item -> Objects.toString(item.get(field), ""))
                .filter(value -> value.startsWith(today)).count();
    }

    private String role(Authentication authentication) {
        return authentication.getAuthorities().stream().findFirst().map(Object::toString)
                .orElse("ROLE_USER").replace("ROLE_", "");
    }
}
