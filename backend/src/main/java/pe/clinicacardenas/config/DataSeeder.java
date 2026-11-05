package pe.clinicacardenas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.clinicacardenas.agenda.*;
import pe.clinicacardenas.clinical.ClinicalService;
import pe.clinicacardenas.clinical.ProcedureRepository;
import pe.clinicacardenas.clinical.Procedure;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientService;
import pe.clinicacardenas.patient.PatientValidator;
import pe.clinicacardenas.user.Role;
import pe.clinicacardenas.user.UserRepository;
import pe.clinicacardenas.user.UserService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Crea las cuentas iniciales (contraseña Clinica2026) y, si app.seed-demo-data=true,
 * pacientes y citas de demostración para que el sistema no arranque vacío.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Clinica2026";

    private final UserRepository users;
    private final UserService userService;
    private final PatientService patientService;
    private final AgendaService agenda;
    private final AppointmentRepository appointments;
    private final DentistRepository dentists;
    private final ClinicalService clinical;
    private final ProcedureRepository procedures;
    private final boolean seedDemo;

    public DataSeeder(UserRepository users, UserService userService, PatientService patientService, AgendaService agenda,
                      AppointmentRepository appointments, DentistRepository dentists, ClinicalService clinical,
                      ProcedureRepository procedures, @Value("${app.seed-demo-data}") boolean seedDemo) {
        this.users = users;
        this.userService = userService;
        this.patientService = patientService;
        this.agenda = agenda;
        this.appointments = appointments;
        this.dentists = dentists;
        this.clinical = clinical;
        this.procedures = procedures;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        log.info("Base de datos vacía: creando cuentas iniciales");
        userService.create(new UserService.UserRequest("admin", "admin@clinicacardenas.pe", "Juan Paredes Cárdenas", Role.ADMIN, DEMO_PASSWORD, null, null));
        userService.create(new UserService.UserRequest("recepcion", "recepcion@clinicacardenas.pe", "Lucía Mendoza Flores", Role.RECEPCION, DEMO_PASSWORD, null, null));
        userService.create(new UserService.UserRequest("dra.cardenas", "rcardenas@clinicacardenas.pe", "Dra. Rosa Cárdenas Vílchez", Role.ODONTOLOGO, DEMO_PASSWORD, "Ortodoncia", "COP 18452"));
        userService.create(new UserService.UserRequest("dr.ramos", "aramos@clinicacardenas.pe", "Dr. Andrés Ramos Seminario", Role.ODONTOLOGO, DEMO_PASSWORD, "Endodoncia", "COP 21307"));
        userService.create(new UserService.UserRequest("caja", "caja@clinicacardenas.pe", "Milagros Távara Ruiz", Role.CAJA, DEMO_PASSWORD, null, null));
        userService.create(new UserService.UserRequest("director", "direccion@clinicacardenas.pe", "Dr. Carlos Cárdenas Ortiz", Role.DIRECTOR, DEMO_PASSWORD, null, null));
        if (seedDemo) {
            try {
                seedDemoData();
            } catch (Exception e) {
                log.warn("No se pudieron crear todos los datos de demostración: {}", e.getMessage());
            }
        }
        log.info("Listo. Usuarios: admin, recepcion, dra.cardenas, dr.ramos, caja, director · contraseña {}", DEMO_PASSWORD);
    }

    private Patient patient(String dni, String first, String last, String birth, String sex, String phone, String email, String district) {
        return patientService.create(new PatientValidator.Input("DNI", dni, first, last, LocalDate.parse(birth), sex, phone,
                email, null, district, null, null, null));
    }

    private void seedDemoData() {
        List<Patient> ps = new ArrayList<>();
        ps.add(patient("45872196", "María Fernanda", "Quispe Huamán", "1991-03-14", "F", "987654321", "mquispe@gmail.com", "Chaclacayo"));
        ps.add(patient("70214583", "José Luis", "García Rojas", "1985-07-02", "M", "956123478", "jlgarcia@hotmail.com", "Lurigancho-Chosica"));
        ps.add(patient("41236985", "Ana Lucía", "Torres Paz", "1978-11-21", "F", "944556677", null, "Chaclacayo"));
        ps.add(patient("72589634", "Diego Alonso", "Chávez Medina", "2001-01-09", "M", "923874561", "dchavez@gmail.com", "Ate"));
        ps.add(patient("46985231", "Rosa Elena", "Sánchez Villegas", "1966-05-30", "F", "961238745", null, "Chaclacayo"));
        ps.add(patient("73659812", "Valeria", "Ramírez Silva", "2010-09-17", "F", "912345678", "familia.ramirez@gmail.com", "Santa Eulalia"));
        ps.add(patient("40125896", "Carlos Alberto", "Zapata Ojeda", "1972-02-11", "M", "978451236", null, "Chaclacayo"));
        ps.add(patient("75896321", "Camila", "Flores Ancajima", "1998-12-03", "F", "934567812", "camila.flores@gmail.com", "Lurigancho-Chosica"));

        List<Dentist> ds = dentists.findAvailable();
        if (ds.size() < 2) return;

        // Citas de hoy (si queda horario) y del siguiente día hábil.
        LocalDate today = Dates.today();
        LocalDate next = today.plusDays(1);
        if (next.getDayOfWeek() == DayOfWeek.SUNDAY) next = next.plusDays(1);
        String[] reasons = {"Control de ortodoncia", "Dolor en molar inferior", "Profilaxis semestral", "Evaluación inicial",
                "Endodoncia - segunda sesión", "Revisión de resina", "Blanqueamiento - control", "Evaluación de tercera molar"};
        int[][] plan = {{9, 0}, {9, 30}, {10, 30}, {11, 0}, {15, 0}, {15, 30}, {16, 30}, {17, 30}};
        int k = 0;
        for (LocalDate day : List.of(today, next)) {
            for (int i = 0; i < plan.length; i++) {
                LocalDateTime start = day.atTime(plan[i][0], plan[i][1]);
                if (start.isBefore(Dates.now().plusMinutes(5))) continue;
                Dentist d = ds.get(i % 2);
                Patient p = ps.get((i + k) % ps.size());
                try {
                    agenda.create(new AgendaService.AppointmentRequest(p.id, d.id, start, i % 3 == 0 ? 60 : 30,
                            reasons[(i + k) % reasons.length], null, null));
                } catch (Exception ignored) {
                    // horario no disponible (p. ej. sábado por la tarde): se omite
                }
            }
            k += 3;
        }

        // Atenciones pasadas para dar contexto a la ficha del paciente.
        for (int i = 0; i < 3; i++) {
            Appointment a = new Appointment();
            a.patient = ps.get(i);
            a.dentist = ds.get(i % 2);
            a.startAt = today.minusDays(7L * (i + 1)).atTime(LocalTime.of(10, 0));
            a.endAt = a.startAt.plusMinutes(30);
            a.durationMin = 30;
            a.reason = "Evaluación inicial";
            a.status = AppointmentStatus.ATENDIDA;
            a.createdAt = a.startAt.minusDays(2);
            a.createdBy = "recepcion";
            appointments.save(a);
        }

        // Historia clínica, odontograma y plan de la primera paciente, registrados como el Dr. Ramos (endodoncia).
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("dr.ramos", null, java.util.List.of()));
        Long pid = ps.get(0).id;
        clinical.saveHistory(pid, new ClinicalService.HistoryRequest("O+", "Penicilina", "Gastritis crónica controlada",
                "Omeprazol 20 mg", "Apendicectomía (2012)", "Bruxismo nocturno", "Madre con diabetes tipo 2",
                "Paciente ansiosa ante procedimientos; explicar cada paso."));
        clinical.addDiagnosis(pid, new ClinicalService.DiagnosisRequest("K02.1", "Caries de la dentina en pieza 36, cara oclusal", 36));
        clinical.addDiagnosis(pid, new ClinicalService.DiagnosisRequest("K04.0", "Pulpitis irreversible en pieza 46", 46));
        clinical.saveOdontogram(pid, new ClinicalService.OdontogramRequest(List.of(
                new ClinicalService.OdontogramMark(36, "O", "CARIES", null),
                new ClinicalService.OdontogramMark(36, "M", "CARIES", null),
                new ClinicalService.OdontogramMark(46, "X", "ENDODONCIA_INDICADA", null),
                new ClinicalService.OdontogramMark(16, "O", "RESTAURACION", "Resina 2023"),
                new ClinicalService.OdontogramMark(26, "O", "RESTAURACION", null),
                new ClinicalService.OdontogramMark(18, "X", "AUSENTE", null),
                new ClinicalService.OdontogramMark(48, "X", "EXTRACCION_INDICADA", null),
                new ClinicalService.OdontogramMark(11, "V", "FRACTURA", "Fractura de esmalte"))));
        Map<String, Long> proc = new HashMap<>();
        for (Procedure p : procedures.findAll()) proc.put(p.code, p.id);
        Map<String, Object> created = clinical.createPlan(pid, new ClinicalService.PlanRequest("Rehabilitación sector posterior",
                "Priorizar endodoncia de 46 antes de restauraciones.", List.of(
                new ClinicalService.PlanItemRequest(proc.get("CON-001"), null, null),
                new ClinicalService.PlanItemRequest(proc.get("RAD-001"), 46, null),
                new ClinicalService.PlanItemRequest(proc.get("END-002"), 46, null),
                new ClinicalService.PlanItemRequest(proc.get("OPE-002"), 36, null),
                new ClinicalService.PlanItemRequest(proc.get("CIR-002"), 48, null))));
        @SuppressWarnings("unchecked")
        List<pe.clinicacardenas.clinical.TreatmentItem> items = (List<pe.clinicacardenas.clinical.TreatmentItem>) created.get("items");
        pe.clinicacardenas.clinical.TreatmentPlan tp = (pe.clinicacardenas.clinical.TreatmentPlan) created.get("plan");
        clinical.addEvolution(tp.id, new ClinicalService.EvolutionRequest(items.get(0).id, today.minusDays(7),
                "Evaluación clínica y radiográfica. Se explica plan y presupuesto.", true));
        clinical.addEvolution(tp.id, new ClinicalService.EvolutionRequest(items.get(1).id, today.minusDays(7),
                "Rx periapical de 46: lesión periapical compatible con pulpitis.", true));
        clinical.addEvolution(tp.id, new ClinicalService.EvolutionRequest(items.get(2).id, today.minusDays(1),
                "Apertura cameral y conductometría. Medicación intraconducto con hidróxido de calcio.", false));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
