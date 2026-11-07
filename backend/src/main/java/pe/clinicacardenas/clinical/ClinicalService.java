package pe.clinicacardenas.clinical;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Dentist;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;
import pe.clinicacardenas.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClinicalService {

    private final PatientRepository patients;
    private final ClinicalHistoryRepository histories;
    private final DiagnosisRepository diagnoses;
    private final OdontogramEntryRepository odontogram;
    private final ProcedureRepository procedures;
    private final TreatmentPlanRepository plans;
    private final TreatmentItemRepository items;
    private final EvolutionRepository evolutions;
    private final DentistRepository dentists;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;

    public ClinicalService(PatientRepository patients, ClinicalHistoryRepository histories, DiagnosisRepository diagnoses,
                           OdontogramEntryRepository odontogram, ProcedureRepository procedures,
                           TreatmentPlanRepository plans, TreatmentItemRepository items, EvolutionRepository evolutions,
                           DentistRepository dentists, UserRepository users, AuditService audit, JdbcTemplate jdbc) {
        this.patients = patients;
        this.histories = histories;
        this.diagnoses = diagnoses;
        this.odontogram = odontogram;
        this.procedures = procedures;
        this.plans = plans;
        this.items = items;
        this.evolutions = evolutions;
        this.dentists = dentists;
        this.users = users;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ DTOs de entrada
    public record HistoryRequest(String bloodType, String allergies, String medicalBackground, String currentMedication,
                                 String surgeries, String habits, String familyBackground, String observations) {}
    public record DiagnosisRequest(String cie10Code, String description, Integer tooth) {}
    public record OdontogramMark(int tooth, String face, String condition, String note) {}
    public record OdontogramRequest(List<OdontogramMark> entries) {}
    public record PlanItemRequest(Long procedureId, Integer tooth, BigDecimal price) {}
    public record PlanRequest(String title, String notes, List<PlanItemRequest> items) {}
    public record ItemStatusRequest(String status) {}
    public record EvolutionRequest(Long itemId, LocalDate sessionDate, String notes, Boolean markDone) {}

    private Patient patient(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    private Dentist currentDentist() {
        return users.findByUsernameIgnoreCase(CurrentUser.usernameOrSystem())
                .flatMap(u -> dentists.findByUserId(u.id)).orElse(null);
    }

    /**
     * Cambia el estado de un procedimiento registrando quién y cuándo lo realizó. Un procedimiento ya cobrado
     * o liquidado en comisiones no puede volver a pendiente: se perdería la trazabilidad de caja.
     */
    private void applyItemStatus(TreatmentItem i, String status, TreatmentPlan p) {
        boolean wasDone = "REALIZADO".equals(i.status);
        boolean nowDone = "REALIZADO".equals(status);
        if (wasDone && !nowDone) {
            Long paid = jdbc.queryForObject("select count(*) from payments where treatment_item_id = ? and status = 'VALIDO'", Long.class, i.id);
            Long settled = jdbc.queryForObject("select count(*) from commission_lines where treatment_item_id = ?", Long.class, i.id);
            if (paid != null && paid > 0) {
                throw ApiException.conflict("ITEM_COBRADO", "\"" + i.procedure.name + "\" ya tiene pagos registrados; no puede volver a pendiente.");
            }
            if (settled != null && settled > 0) {
                throw ApiException.conflict("ITEM_LIQUIDADO", "\"" + i.procedure.name + "\" ya está incluido en una liquidación de comisiones.");
            }
        }
        i.status = status;
        if (nowDone && !wasDone) {
            i.doneAt = Dates.now();
            Dentist d = currentDentist();
            i.doneByDentistId = d != null ? d.id : (p.dentist == null ? null : p.dentist.id);
        } else if (!nowDone) {
            i.doneAt = null;
            i.doneByDentistId = null;
        }
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ HU-04 historia clínica

    @Transactional(readOnly = true)
    public Map<String, Object> history(Long patientId) {
        Patient p = patient(patientId);
        ClinicalHistory h = histories.findByPatientId(patientId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient", p);
        m.put("history", h);
        m.put("diagnoses", diagnoses.findByPatientIdOrderByCreatedAtDesc(patientId));
        List<String> alerts = new ArrayList<>();
        if (h != null && h.allergies != null && !h.allergies.isBlank()) alerts.add("Alergias: " + h.allergies);
        if (h != null && h.currentMedication != null && !h.currentMedication.isBlank()) alerts.add("Medicación: " + h.currentMedication);
        m.put("alerts", alerts);
        return m;
    }

    @Transactional
    public ClinicalHistory saveHistory(Long patientId, HistoryRequest r) {
        patient(patientId);
        ClinicalHistory h = histories.findByPatientId(patientId).orElse(null);
        boolean isNew = h == null;
        if (isNew) {
            h = new ClinicalHistory();
            h.patientId = patientId;
            h.createdAt = Dates.now();
        }
        h.bloodType = clean(r.bloodType());
        h.allergies = clean(r.allergies());
        h.medicalBackground = clean(r.medicalBackground());
        h.currentMedication = clean(r.currentMedication());
        h.surgeries = clean(r.surgeries());
        h.habits = clean(r.habits());
        h.familyBackground = clean(r.familyBackground());
        h.observations = clean(r.observations());
        h.updatedAt = Dates.now();
        h.updatedBy = CurrentUser.usernameOrSystem();
        histories.save(h);
        audit.record("HISTORIA", patientId, isNew ? "CREAR" : "EDITAR", "Antecedentes clínicos");
        return h;
    }

    @Transactional
    public Diagnosis addDiagnosis(Long patientId, DiagnosisRequest r) {
        patient(patientId);
        if (r.description() == null || r.description().isBlank()) {
            throw ApiException.field("description", "Describe el diagnóstico.");
        }
        if (r.tooth() != null && !OdontogramRules.isValidTooth(r.tooth())) {
            throw ApiException.field("tooth", "La pieza no existe en la notación FDI.");
        }
        Diagnosis d = new Diagnosis();
        d.patientId = patientId;
        d.dentist = currentDentist();
        d.cie10Code = clean(r.cie10Code()) == null ? null : r.cie10Code().trim().toUpperCase();
        d.description = r.description().trim();
        d.tooth = r.tooth();
        d.createdAt = Dates.now();
        d.createdBy = CurrentUser.usernameOrSystem();
        diagnoses.save(d);
        audit.record("HISTORIA", patientId, "DIAGNOSTICO", (d.cie10Code == null ? "" : d.cie10Code + " ") + d.description);
        return d;
    }

    // ------------------------------------------------------------------ HU-05 odontograma

    @Transactional(readOnly = true)
    public Map<String, Object> odontogram(Long patientId) {
        patient(patientId);
        List<OdontogramEntry> list = odontogram.findByPatientIdOrderByToothAscFaceAsc(patientId);
        LocalDateTime last = null;
        String by = null;
        for (OdontogramEntry e : list) {
            if (last == null || e.recordedAt.isAfter(last)) {
                last = e.recordedAt;
                by = e.recordedBy;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entries", list);
        m.put("updatedAt", last);
        m.put("updatedBy", by);
        return m;
    }

    @Transactional
    public Map<String, Object> saveOdontogram(Long patientId, OdontogramRequest r) {
        patient(patientId);
        List<OdontogramMark> marks = r.entries() == null ? List.of() : r.entries();
        Map<String, OdontogramEntry> previous = new HashMap<>();
        for (OdontogramEntry e : odontogram.findByPatientIdOrderByToothAscFaceAsc(patientId)) {
            previous.put(e.tooth + e.face + e.conditionCode, e);
        }
        Set<String> seen = new HashSet<>();
        List<OdontogramEntry> toSave = new ArrayList<>();
        LocalDateTime now = Dates.now();
        String user = CurrentUser.usernameOrSystem();
        for (OdontogramMark mk : marks) {
            String face = mk.face() == null ? "" : mk.face().toUpperCase();
            String cond = mk.condition() == null ? "" : mk.condition().toUpperCase();
            String err = OdontogramRules.check(mk.tooth(), face, cond);
            if (err != null) throw ApiException.badRequest("ODONTOGRAMA_INVALIDO", err);
            if (!seen.add(mk.tooth() + face)) {
                throw ApiException.badRequest("ODONTOGRAMA_INVALIDO", "La pieza " + mk.tooth() + " tiene dos marcas en la misma cara.");
            }
            OdontogramEntry prev = previous.get(mk.tooth() + face + cond);
            OdontogramEntry e = new OdontogramEntry();
            e.patientId = patientId;
            e.tooth = mk.tooth();
            e.face = face;
            e.conditionCode = cond;
            e.note = clean(mk.note());
            // Se conserva quién y cuándo registró una marca que no cambió.
            e.recordedAt = prev != null ? prev.recordedAt : now;
            e.recordedBy = prev != null ? prev.recordedBy : user;
            toSave.add(e);
        }
        odontogram.deleteByPatientId(patientId);
        odontogram.flush();
        odontogram.saveAll(toSave);
        audit.record("HISTORIA", patientId, "ODONTOGRAMA", toSave.size() + " marcas registradas");
        return odontogram(patientId);
    }

    // ------------------------------------------------------------------ HU-06 plan y evolución

    public List<Procedure> procedures() {
        return procedures.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> plans(Long patientId) {
        patient(patientId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TreatmentPlan p : plans.findByPatientIdOrderByCreatedAtDesc(patientId)) {
            out.add(planView(p));
        }
        return out;
    }

    private Map<String, Object> planView(TreatmentPlan p) {
        List<TreatmentItem> its = items.findByPlanIdOrderBySortOrderAscIdAsc(p.id);
        BigDecimal total = BigDecimal.ZERO, done = BigDecimal.ZERO;
        int doneCount = 0;
        for (TreatmentItem i : its) {
            total = total.add(i.price);
            if ("REALIZADO".equals(i.status)) {
                done = done.add(i.price);
                doneCount++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", p);
        m.put("items", its);
        m.put("evolutions", evolutions.findByPlanIdOrderBySessionDateDescIdDesc(p.id));
        m.put("total", total);
        m.put("doneAmount", done);
        m.put("doneCount", doneCount);
        m.put("itemCount", its.size());
        return m;
    }

    private TreatmentPlan plan(Long id) {
        return plans.findById(id).orElseThrow(() -> ApiException.notFound("El plan de tratamiento"));
    }

    private TreatmentItem buildItem(Long planId, PlanItemRequest r, int order) {
        if (r.procedureId() == null) throw ApiException.field("items", "Selecciona el procedimiento.");
        Procedure proc = procedures.findById(r.procedureId()).orElseThrow(() -> ApiException.notFound("El procedimiento"));
        if (r.tooth() != null && !OdontogramRules.isValidTooth(r.tooth())) {
            throw ApiException.field("items", "La pieza " + r.tooth() + " no existe en la notación FDI.");
        }
        BigDecimal price = r.price() == null ? proc.price : r.price();
        if (price.signum() < 0) throw ApiException.field("items", "El precio no puede ser negativo.");
        TreatmentItem i = new TreatmentItem();
        i.planId = planId;
        i.procedure = proc;
        i.tooth = r.tooth();
        i.price = price.setScale(2, java.math.RoundingMode.HALF_UP);
        i.status = "PENDIENTE";
        i.sortOrder = order;
        return i;
    }

    @Transactional
    public Map<String, Object> createPlan(Long patientId, PlanRequest r) {
        patient(patientId);
        if (r.title() == null || r.title().isBlank()) throw ApiException.field("title", "Ponle un nombre al plan.");
        if (r.items() == null || r.items().isEmpty()) throw ApiException.field("items", "Agrega al menos un procedimiento.");
        TreatmentPlan p = new TreatmentPlan();
        p.patientId = patientId;
        p.dentist = currentDentist();
        p.title = r.title().trim();
        p.notes = clean(r.notes());
        p.status = "ACTIVO";
        p.createdAt = Dates.now();
        p.createdBy = CurrentUser.usernameOrSystem();
        plans.save(p);
        int order = 0;
        for (PlanItemRequest ir : r.items()) {
            items.save(buildItem(p.id, ir, order++));
        }
        audit.record("HISTORIA", patientId, "PLAN_CREAR", p.title + " · " + r.items().size() + " procedimientos");
        return planView(p);
    }

    @Transactional
    public Map<String, Object> addItem(Long planId, PlanItemRequest r) {
        TreatmentPlan p = plan(planId);
        if (!"ACTIVO".equals(p.status)) throw ApiException.badRequest("PLAN_CERRADO", "El plan ya no está activo.");
        int order = items.findByPlanIdOrderBySortOrderAscIdAsc(planId).size();
        items.save(buildItem(planId, r, order));
        audit.record("HISTORIA", p.patientId, "PLAN_ITEM", "Procedimiento agregado a " + p.title);
        return planView(p);
    }

    @Transactional
    public Map<String, Object> setItemStatus(Long itemId, String status) {
        TreatmentItem i = items.findById(itemId).orElseThrow(() -> ApiException.notFound("El procedimiento del plan"));
        if (!Set.of("PENDIENTE", "EN_PROCESO", "REALIZADO").contains(status)) {
            throw ApiException.field("status", "Estado inválido.");
        }
        TreatmentPlan p = plan(i.planId);
        if ("ANULADO".equals(p.status)) throw ApiException.badRequest("PLAN_CERRADO", "El plan está anulado.");
        applyItemStatus(i, status, p);
        items.save(i);
        refreshPlanStatus(p);
        audit.record("HISTORIA", p.patientId, "PLAN_ESTADO", i.procedure.name + " → " + status.toLowerCase().replace('_', ' '));
        return planView(p);
    }

    private void refreshPlanStatus(TreatmentPlan p) {
        List<TreatmentItem> its = items.findByPlanIdOrderBySortOrderAscIdAsc(p.id);
        boolean allDone = !its.isEmpty() && its.stream().allMatch(x -> "REALIZADO".equals(x.status));
        String next = allDone ? "COMPLETADO" : "ACTIVO";
        if (!next.equals(p.status)) {
            p.status = next;
            plans.save(p);
        }
    }

    @Transactional
    public Map<String, Object> addEvolution(Long planId, EvolutionRequest r) {
        TreatmentPlan p = plan(planId);
        if (r.notes() == null || r.notes().isBlank()) throw ApiException.field("notes", "Describe lo realizado en la sesión.");
        LocalDate date = r.sessionDate() == null ? Dates.today() : r.sessionDate();
        if (date.isAfter(Dates.today())) throw ApiException.field("sessionDate", "La fecha de la sesión no puede ser futura.");
        TreatmentItem item = null;
        if (r.itemId() != null) {
            item = items.findById(r.itemId()).orElseThrow(() -> ApiException.notFound("El procedimiento del plan"));
            if (!item.planId.equals(planId)) throw ApiException.badRequest("ITEM_AJENO", "El procedimiento no pertenece a este plan.");
        }
        Evolution e = new Evolution();
        e.planId = planId;
        e.itemId = item == null ? null : item.id;
        e.dentist = currentDentist();
        e.sessionDate = date;
        e.notes = r.notes().trim();
        e.createdAt = Dates.now();
        e.createdBy = CurrentUser.usernameOrSystem();
        evolutions.save(e);
        if (item != null) {
            String next = Boolean.TRUE.equals(r.markDone()) ? "REALIZADO" : ("PENDIENTE".equals(item.status) ? "EN_PROCESO" : item.status);
            applyItemStatus(item, next, p);
            items.save(item);
        }
        refreshPlanStatus(p);
        audit.record("HISTORIA", p.patientId, "EVOLUCION", "Sesión del " + date + " en " + p.title);
        return planView(p);
    }

    @Transactional
    public Map<String, Object> cancelPlan(Long planId) {
        TreatmentPlan p = plan(planId);
        p.status = "ANULADO";
        plans.save(p);
        audit.record("HISTORIA", p.patientId, "PLAN_ANULAR", p.title);
        return planView(p);
    }

    /** Usado por el resumen gerencial y por caja. */
    public long countItems(String status) {
        return items.countByStatus(status);
    }

    static void ensure(boolean cond, String msg) {
        if (!cond) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", msg);
    }
}
