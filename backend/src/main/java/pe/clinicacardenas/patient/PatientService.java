package pe.clinicacardenas.patient;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Appointment;
import pe.clinicacardenas.agenda.AppointmentRepository;
import pe.clinicacardenas.agenda.AppointmentStatus;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.notification.ContactSync;
import pe.clinicacardenas.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PatientService {
    private static final String DOCNUMBER = "docNumber";
    private static final String FIRSTNAME = "firstName";
    private static final String LASTNAME = "lastName";


    private final PatientRepository repo;
    private final AppointmentRepository appointments;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private ConsentService consents;

    @org.springframework.beans.factory.annotation.Autowired
    void setConsents(@org.springframework.context.annotation.Lazy ConsentService consents) {
        this.consents = consents;
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private pe.clinicacardenas.clinical.AnamnesisService anamnesis;

    @org.springframework.beans.factory.annotation.Autowired
    void setAnamnesis(@org.springframework.context.annotation.Lazy pe.clinicacardenas.clinical.AnamnesisService anamnesis) {
        this.anamnesis = anamnesis;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setJdbc(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PatientService(PatientRepository repo, AppointmentRepository appointments, AuditService audit,
                          ApplicationEventPublisher events) {
        this.events = events;
        this.repo = repo;
        this.appointments = appointments;
        this.audit = audit;
    }

    public Patient get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("El paciente"));
    }

    // ------------------------------------------------------------------ HU-02 búsqueda

    @Transactional(readOnly = true)
    public Map<String, Object> search(String q, int page, int size) {
        return search(q, page, size, false);
    }

    public Map<String, Object> search(String q, int page, int size, boolean mine) {
        return search(q, page, size, mine, null, null);
    }

    /** mine = solo los pacientes que tienen o tuvieron cita con el odontólogo que consulta ("Mis pacientes").
     *  tag / source (v2.5) filtran por etiqueta y por fuente de captación. */
    public Map<String, Object> search(String q, int page, int size, boolean mine, String tag, String source) {
        return search(q, page, size, mine, tag, source, null);
    }

    /** v2.7: además, filtros de seguimiento (tarea pendiente, próxima cita, presupuesto, saldo…) de la lista de pacientes. */
    public Map<String, Object> search(String q, int page, int size, boolean mine, String tag, String source, pe.clinicacardenas.crm.SegmentFilter extra) {
        final List<Long> extraIds;
        if (extra != null && !extra.isEmpty()) {
            var w = extra.where(Dates.now());
            extraIds = jdbc.queryForList("select p.id from patients p where " + w.sql(), Long.class, w.args().toArray());
        } else extraIds = null;
        Specification<Patient> filter = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (extraIds != null) ps.add(extraIds.isEmpty() ? cb.disjunction() : root.get("id").in(extraIds));
            if (source != null && !source.isBlank()) ps.add(cb.equal(root.get("source"), source.trim().toUpperCase()));
            if (tag != null && !tag.isBlank()) {
                List<Long> ids = jdbc.queryForList("select patient_id from patient_tags where tag = ?", Long.class, tag.trim());
                ps.add(ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        String query = q == null ? "" : q.trim();
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50));
        Page<Patient> result;
        String mode;
        if (mine) {
            List<Long> ids = jdbc.queryForList("""
                    select distinct a.patient_id from appointments a join dentists d on d.id = a.dentist_id
                      join users u on u.id = d.user_id where lower(u.username) = lower(?)""", Long.class, pe.clinicacardenas.security.CurrentUser.usernameOrSystem());
            Specification<Patient> own = (root, cq, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
            Specification<Patient> text = (root, cq, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                for (String t : query.toLowerCase().split("\\s+")) {
                    if (t.isBlank()) continue;
                    String like = "%" + t.replace("%", "").replace("_", "") + "%";
                    ps.add(cb.or(cb.like(cb.lower(root.<String>get(FIRSTNAME)), like), cb.like(cb.lower(root.<String>get(LASTNAME)), like),
                            cb.like(root.<String>get(DOCNUMBER), like)));
                }
                return cb.and(ps.toArray(new Predicate[0]));
            };
            mode = "mis-pacientes";
            result = repo.findAll(own.and(text).and(filter), pr.withSort(Sort.by(LASTNAME, FIRSTNAME)));
        } else if (query.isEmpty()) {
            mode = "recientes";
            result = repo.findAll(filter, pr.withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        } else if (query.matches("^[0-9A-Za-z]{5,15}$") && query.chars().anyMatch(Character::isDigit)) {
            mode = "documento";
            String doc = PatientValidator.normalizeDoc(query);
            Specification<Patient> spec = (root, cq, cb) -> cb.or(cb.like(root.<String>get(DOCNUMBER), doc + "%"),
                    cb.equal(root.<String>get("hcNumber"), query));
            result = repo.findAll(spec.and(filter), pr.withSort(Sort.by(DOCNUMBER)));
        } else {
            mode = "nombre";
            String[] tokens = query.toLowerCase().split("\\s+");
            Specification<Patient> spec = (root, cq, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                for (String t : tokens) {
                    if (t.isBlank()) continue;
                    String like = "%" + t.replace("%", "").replace("_", "") + "%";
                    ps.add(cb.or(cb.like(cb.lower(root.<String>get(FIRSTNAME)), like),
                                 cb.like(cb.lower(root.<String>get(LASTNAME)), like)));
                }
                return cb.and(ps.toArray(new Predicate[0]));
            };
            result = repo.findAll(spec.and(filter), pr.withSort(Sort.by(LASTNAME, FIRSTNAME)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("content", result.getContent());
        out.put("total", result.getTotalElements());
        out.put("page", result.getNumber());
        out.put("pages", result.getTotalPages());
        return out;
    }

    /** Ficha resumen del paciente con su próxima cita. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long id) {
        Patient p = get(id);
        List<Appointment> appts = appointments.findByPatientIdOrderByStartAtDesc(id);
        LocalDateTime now = Dates.now();
        Appointment next = null;
        Appointment last = null;
        for (Appointment a : appts) {
            if (a.startAt.isAfter(now) && AppointmentStatus.BLOCKING.contains(a.status)) {
                next = a; // la lista está en orden descendente: la última asignada es la más próxima
            }
            if (last == null && a.status == AppointmentStatus.ATENDIDA) {
                last = a;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient", p);
        m.put("nextAppointment", next);
        m.put("lastVisit", last);
        m.put("appointmentsCount", appts.size());
        if (jdbc != null) {
            // Lo que el odontólogo necesita ver antes de atender: alertas médicas y procedimientos pendientes del plan.
            List<Map<String, Object>> h = jdbc.queryForList(
                    "select allergies, current_medication as medication, medical_background as background, blood_type as bloodType from clinical_histories where patient_id = ?", id);
            m.put("clinicalAlerts", h.isEmpty() ? null : h.get(0));
            if (anamnesis != null) m.put("anamnesisAlerts", anamnesis.alerts(id));
            m.put("pendingProcedures", jdbc.queryForList(
                    "select ti.id, pr.name as `procedure`, ti.tooth, ti.price, tp.title as plan, u.full_name as dentist"
                    + " from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id"
                    + " join procedures pr on pr.id = ti.procedure_id"
                    + " left join dentists d on d.id = tp.dentist_id left join users u on u.id = d.user_id"
                    + " where tp.patient_id = ? and ti.status = 'PENDIENTE' and tp.status <> 'ANULADO'"
                    + " order by ti.sort_order, ti.id limit 10", id));
            List<Long> mine = jdbc.queryForList(
                    "select d.id from dentists d join users u on u.id = d.user_id where lower(u.username) = lower(?)", Long.class, CurrentUser.usernameOrSystem());
            m.put("nextIsMine", next != null && !mine.isEmpty() && next.dentist != null && mine.get(0).equals(next.dentist.id));
        }
        return m;
    }

    // ------------------------------------------------------------------ HU-01 registro

    /** Comprueba en vivo si el documento ya está registrado. */
    @Transactional(readOnly = true)
    public Map<String, Object> checkDocument(String type, String number, Long excludeId) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<Patient> found = repo.findByDocTypeAndDocNumber(
                type == null ? "" : type.toUpperCase(), PatientValidator.normalizeDoc(number));
        boolean dup = found.isPresent() && !Objects.equals(found.get().id, excludeId);
        m.put("exists", dup);
        if (dup) {
            Patient p = found.get();
            m.put("patient", Map.of("id", p.id, "fullName", p.getFullName(), DOCNUMBER, p.docNumber));
        }
        return m;
    }

    @Transactional
    public Patient create(PatientValidator.Input in) {
        return create(in, false);
    }

    /** Paciente de ejemplo para la demostración: queda marcado y no recibe WhatsApp ni correos reales. */
    @Transactional
    public Patient createDemo(PatientValidator.Input in) {
        return create(in, true);
    }

    /** v2.6: el paciente se registra solo desde el enlace público: consentimiento por canal WEB con su IP. */
    @Transactional
    public Patient createFromWeb(PatientValidator.Input in, String ip) {
        WEB_IP.set(ip == null ? "" : ip);
        try { return create(in, false); } finally { WEB_IP.remove(); }
    }

    private static final ThreadLocal<String> WEB_IP = new ThreadLocal<>();

    private Patient create(PatientValidator.Input in, boolean demo) {
        Map<String, String> errors = PatientValidator.validate(in, Dates.today());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        }
        if (!demo && !Boolean.TRUE.equals(in.dataConsent())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_CONSENTIMIENTO",
                    "Para registrar al paciente debe aceptar la política de privacidad (Ley N.° 29733: los datos de salud requieren consentimiento expreso).",
                    Map.of("dataConsent", "Lee la política con el paciente (o su representante legal) y marca la aceptación."));
        }
        String type = in.docType().trim().toUpperCase();
        String doc = PatientValidator.normalizeDoc(in.docNumber());
        repo.findByDocTypeAndDocNumber(type, doc).ifPresent(existing -> {
            throw ApiException.conflict("PACIENTE_DUPLICADO",
                    "Ya existe un paciente con " + type + " " + doc + ": " + existing.getFullName() + ".",
                    Map.of(DOCNUMBER, "Documento ya registrado.", "existingId", String.valueOf(existing.id)));
        });
        Patient p = new Patient();
        apply(p, in);
        p.docType = type;
        p.docNumber = doc;
        p.createdAt = Dates.now();
        p.createdBy = CurrentUser.usernameOrSystem();
        p.demo = demo;
        repo.save(p);
        if (p.hcNumber == null) { p.hcNumber = String.format("%06d", p.id); repo.save(p); }
        if (p.dataConsent && consents != null) {
            if (WEB_IP.get() != null) consents.register(p, "PRIVACIDAD", "WEB", "paciente (enlace web)", WEB_IP.get().isBlank() ? null : WEB_IP.get());
            else consents.recordInitial(p);
        }
        audit.register("PACIENTE", p.id, "CREAR", p.getFullName() + " · " + type + " " + doc + (WEB_IP.get() != null ? " · registrado desde la web" : ""));
        events.publishEvent(new ContactSync.PatientSaved(p.id));
        return p;
    }

    /** HU-03: actualiza datos guardando fecha y usuario de modificación. El documento no se edita. */
    @Transactional
    public Patient update(Long id, PatientValidator.Input in) {
        Patient p = get(id);
        PatientValidator.Input merged = new PatientValidator.Input(p.docType, p.docNumber, in.firstName(), in.lastName(),
                in.birthDate(), in.sex(), in.phone(), in.email(), in.address(), in.district(),
                in.emergencyContactName(), in.emergencyContactPhone(), in.notes(), in.dataConsent(),
                in.ubigeo(), in.emergencyContactRelationship(), in.guardianName(), in.guardianDocType(),
                in.guardianDocNumber(), in.guardianRelationship(), in.guardianPhone());
        Map<String, String> errors = PatientValidator.validate(merged, Dates.today());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        }
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(p.phone, PatientValidator.normalizePhone(in.phone()))) changes.add("teléfono");
        if (!Objects.equals(p.email, blankToNull(in.email()))) changes.add("correo");
        if (!Objects.equals(p.address, blankToNull(in.address()))) changes.add("dirección");
        boolean hadConsent = p.dataConsent;
        apply(p, merged);
        if (!hadConsent && p.dataConsent && consents != null) consents.recordInitial(p);
        p.updatedAt = Dates.now();
        p.updatedBy = CurrentUser.usernameOrSystem();
        repo.save(p);
        audit.register("PACIENTE", p.id, "EDITAR", changes.isEmpty() ? "Datos actualizados" : "Actualizó " + String.join(", ", changes));
        events.publishEvent(new ContactSync.PatientSaved(p.id));
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.trim().toLowerCase().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static void apply(Patient p, PatientValidator.Input in) {
        p.firstName = titleCase(in.firstName());
        p.lastName = titleCase(in.lastName());
        p.birthDate = in.birthDate();
        p.sex = in.sex();
        p.phone = PatientValidator.normalizePhone(in.phone());
        p.email = blankToNull(in.email()) == null ? null : in.email().trim().toLowerCase();
        p.address = blankToNull(in.address());
        p.district = blankToNull(in.district());
        String[] u = Ubigeo.names(blankToNull(in.ubigeo()));
        if (u != null) {
            p.ubigeo = in.ubigeo().trim();
            p.department = u[0];
            p.province = u[1];
            p.district = u[2];
        } else if (blankToNull(in.ubigeo()) == null && in.ubigeo() != null) {
            p.ubigeo = null;
        }
        p.emergencyContactRelationship = blankToNull(in.emergencyContactRelationship());
        if (p.isMinor()) {
            p.guardianName = titleCase(in.guardianName());
            p.guardianDocType = in.guardianDocType() == null || in.guardianDocType().isBlank() ? "DNI" : in.guardianDocType().trim().toUpperCase();
            p.guardianDocNumber = PatientValidator.normalizeDoc(in.guardianDocNumber());
            p.guardianRelationship = in.guardianRelationship();
            p.guardianPhone = PatientValidator.normalizePhone(in.guardianPhone());
        } else if (blankToNull(in.guardianName()) == null) {
            p.guardianName = null; p.guardianDocType = null; p.guardianDocNumber = null; p.guardianRelationship = null; p.guardianPhone = null;
        }
        p.emergencyContactName = blankToNull(in.emergencyContactName());
        p.emergencyContactPhone = blankToNull(in.emergencyContactPhone()) == null ? null
                : PatientValidator.normalizePhone(in.emergencyContactPhone());
        p.notes = blankToNull(in.notes());
        if (Boolean.TRUE.equals(in.dataConsent()) && !p.dataConsent) {
            p.dataConsent = true;
            p.dataConsentAt = pe.clinicacardenas.common.Dates.now();
        }
    }
}
