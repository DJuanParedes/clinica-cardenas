package pe.clinica.cardenas.operationsservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.clinica.cardenas.operationsservice.domain.*;
import pe.clinica.cardenas.operationsservice.repository.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {
    private final EmergencyRepository emergencies;
    private final FollowUpRepository followUps;
    private final ComplaintRepository complaints;
    private final EmployeeRepository employees;
    private final EquipmentRepository equipment;
    private final ModuleRecordRepository moduleRecords;
    private final ObjectMapper objectMapper;

    @Bean
    CommandLineRunner seedOperations() {
        return args -> {
            if (emergencies.count() == 0) {
                emergencies.save(Emergency.builder().patientId(2L).patientName("José Quispe Ramos")
                        .priority("ALTA").emergencyType("Dolor agudo").painLevel(9)
                        .symptoms("Dolor pulsátil e inflamación desde la madrugada")
                        .assignedDoctor("Dr. Diego Salazar").arrivalAt(LocalDateTime.now().minusHours(1))
                        .status(Status.EN_PROCESO).build());
            }
            if (followUps.count() == 0) {
                followUps.save(FollowUp.builder().patientName("Andrea Flores Mendoza")
                        .treatment("Preparación para corona").doctorName("Dra. Valeria Cárdenas")
                        .treatmentDate(LocalDate.now().minusDays(2)).followUpDate(LocalDate.now())
                        .symptoms("Sensibilidad leve")
                        .recommendations("Evitar alimentos muy fríos y confirmar evolución en 48 horas")
                        .followUpStatus("CONTACTADO").build());
                followUps.save(FollowUp.builder().patientName("José Quispe Ramos")
                        .treatment("Atención de emergencia").doctorName("Dr. Diego Salazar")
                        .treatmentDate(LocalDate.now()).followUpDate(LocalDate.now().plusDays(1))
                        .symptoms("Pendiente").recommendations("Llamar mañana a primera hora")
                        .followUpStatus("PENDIENTE").build());
            }
            if (complaints.count() == 0) {
                complaints.save(Complaint.builder().type("SUGERENCIA").patientName("Andrea Flores Mendoza")
                        .area("Recepción").description("Agregar recordatorio un día antes por WhatsApp")
                        .createdAt(LocalDate.now().minusDays(3)).dueDate(LocalDate.now().plusDays(4))
                        .responsible("Jefatura de recepción").response("Pendiente de configuración")
                        .complaintStatus("EN PROCESO").build());
            }
            if (employees.count() == 0) {
                employees.save(Employee.builder().fullName("Dra. Valeria Cárdenas").position("Cirujana dentista")
                        .area("Clínica").branch("Sede Principal").shift("Lunes a sábado 08:00-16:00")
                        .contractEnd(LocalDate.now().plusYears(1)).employeeStatus("ACTIVO").build());
                employees.save(Employee.builder().fullName("María Recepción").position("Recepcionista")
                        .area("Atención al paciente").branch("Sede Principal").shift("Lunes a sábado 08:00-17:00")
                        .contractEnd(LocalDate.now().plusMonths(8)).employeeStatus("ACTIVO").build());
            }
            if (equipment.count() == 0) {
                equipment.save(Equipment.builder().name("Autoclave principal").brand("Woson")
                        .model("Tanda 23L").serialNumber("AUTO-2026-001").location("Central de esterilización")
                        .lastMaintenance(LocalDate.now().minusMonths(2)).nextMaintenance(LocalDate.now().plusMonths(1))
                        .equipmentStatus("OPERATIVO").build());
                equipment.save(Equipment.builder().name("Sillón odontológico 1").brand("Gnatus")
                        .model("G8").serialNumber("SIL-001").location("Consultorio 1")
                        .lastMaintenance(LocalDate.now().minusMonths(4)).nextMaintenance(LocalDate.now().plusDays(12))
                        .equipmentStatus("OPERATIVO").build());
            }
            if (moduleRecords.count() == 0) seedModules();
        };
    }

    private void seedModules() {
        save("clinical-history", "Evaluación integral inicial", "Andrea Flores Mendoza",
                "Anamnesis actualizada, alergias verificadas y diagnóstico preliminar registrado.",
                "ACTIVA", "Dra. Valeria Cárdenas", LocalDate.now(), null);
        save("treatments", "Corona de zirconio pieza 16", "Andrea Flores Mendoza",
                "Plan de tres sesiones: preparación, prueba e instalación.",
                "EN PROCESO", "Dra. Valeria Cárdenas", LocalDate.now().plusDays(4), new BigDecimal("1550.00"));
        save("prescriptions", "Amoxicilina 500 mg", "José Quispe Ramos",
                "Una cápsula cada 8 horas por 7 días. Verificar alergias antes de entregar.",
                "VIGENTE", "Dr. Diego Salazar", LocalDate.now(), null);
        save("inventory", "Guantes de nitrilo talla M", "Lote GN-0826",
                "Stock actual: 18 cajas. Stock mínimo: 10 cajas.",
                "DISPONIBLE", "Almacén central", LocalDate.now().plusMonths(18), new BigDecimal("540.00"));
        save("sterilization", "Ciclo autoclave AC-2026-081", "Bandejas cirugía oral",
                "Indicador químico conforme. Operador: María Recepción.",
                "CONFORME", "Central de esterilización", LocalDate.now(), null);
        save("security", "Revisión de accesos privilegiados", "Usuarios administrativos",
                "No se detectaron accesos fuera del horario autorizado.",
                "REVISADO", "Administrador general", LocalDate.now(), null);
        save("monitoring", "Disponibilidad de microservicios", "Entorno local",
                "Eureka, Gateway y servicios de dominio reportan estado saludable.",
                "SALUDABLE", "Monitoreo automático", LocalDate.now(), null);
        save("backups", "Respaldo automático diario", "Base de datos y documentos",
                "Último respaldo validado y disponible para restauración controlada.",
                "COMPLETADO", "Administrador técnico", LocalDate.now(), null);
        save("integrations", "Canal de correo transaccional", "Notificaciones de citas",
                "Integración registrada en el entorno local; las credenciales externas se configuran por ambiente.",
                "CONFIGURADO", "Administrador general", LocalDate.now(), null);
        save("environments", "Entorno local", "Docker Compose",
                "Servicios aislados con bases MySQL y variables de entorno de prueba.",
                "ACTIVO", "Administrador técnico", LocalDate.now(), null);
        save("quality", "Prueba de permisos por rol", "Frontend y APIs",
                "Matriz de acceso incluida en la prueba automatizada PROBAR-SISTEMA.ps1.",
                "APROBADO", "Control de calidad", LocalDate.now(), null);
        save("migration", "Importación inicial de pacientes", "Plantilla pacientes.xlsx",
                "Vista previa preparada: 48 filas válidas y 2 observadas.",
                "EN REVISIÓN", "Administrador general", LocalDate.now(), null);
        save("settings", "Horario sede principal", "Lunes a sábado",
                "Atención de 08:00 a 22:00. Emergencias sujetas a disponibilidad.",
                "ACTIVO", "Administrador general", LocalDate.now(), null);
    }

    private void save(String moduleKey, String title, String subject, String detail, String status,
                      String responsible, LocalDate eventDate, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (moduleKey) {
            case "clinical-history" -> { payload.put("patientName", subject); payload.put("consultationReason", title); payload.put("diagnosis", detail); }
            case "treatments" -> { payload.put("patientName", subject); payload.put("procedure", title); payload.put("diagnosis", detail); payload.put("sessions", 3); }
            case "prescriptions" -> { payload.put("patientName", subject); payload.put("medication", title); payload.put("dose", detail); payload.put("frequency", "Cada 8 horas"); }
            case "sterilization" -> { payload.put("cycleCode", title); payload.put("equipment", "Autoclave principal"); payload.put("packageCode", subject); payload.put("chemicalIndicator", "CONFORME"); }
            case "security" -> { payload.put("eventType", "PERMISO"); payload.put("userName", subject); payload.put("action", title); payload.put("severity", "MEDIA"); }
            case "monitoring" -> { payload.put("serviceName", subject); payload.put("endpoint", title); payload.put("responseTimeMs", 120); }
            case "backups" -> { payload.put("backupName", title); payload.put("scope", subject); payload.put("location", "Volumen local cifrado"); }
            case "integrations" -> { payload.put("serviceName", title); payload.put("provider", subject); payload.put("environment", "LOCAL"); }
            case "environments" -> { payload.put("name", title); payload.put("environmentType", "LOCAL"); payload.put("url", "http://localhost:8080"); }
            case "quality" -> { payload.put("testName", title); payload.put("module", subject); payload.put("testType", "END TO END"); payload.put("expectedResult", detail); }
            case "migration" -> { payload.put("sourceName", subject); payload.put("fileType", "EXCEL"); payload.put("totalRows", 50); payload.put("validRows", 48); payload.put("errorRows", 2); }
            case "settings" -> { payload.put("name", title); payload.put("category", "HORARIOS"); payload.put("value", detail); payload.put("branch", subject); }
            default -> { }
        }
        String payloadJson;
        try { payloadJson = payload.isEmpty() ? null : objectMapper.writeValueAsString(payload); }
        catch (Exception ex) { payloadJson = null; }
        moduleRecords.save(ModuleRecord.builder().moduleKey(moduleKey).title(title).subject(subject)
                .detail(detail).status(status).responsible(responsible).eventDate(eventDate).amount(amount)
                .payloadJson(payloadJson).createdAt(now).updatedAt(now).build());
    }
}
