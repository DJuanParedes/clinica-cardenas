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

    private final PatientRepository repo;
    private final AppointmentRepository appointments;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

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
        String query = q == null ? "" : q.trim();
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50));
        Page<Patient> result;
        String mode;
        if (query.isEmpty()) {
            mode = "recientes";
            result = repo.findAll(pr.withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        } else if (query.matches("^[0-9A-Za-z]{5,15}$") && query.chars().anyMatch(Character::isDigit)) {
            mode = "documento";
            String doc = PatientValidator.normalizeDoc(query);
            Specification<Patient> spec = (root, cq, cb) -> cb.like(root.<String>get("docNumber"), doc + "%");
            result = repo.findAll(spec, pr.withSort(Sort.by("docNumber")));
        } else {
            mode = "nombre";
            String[] tokens = query.toLowerCase().split("\\s+");
            Specification<Patient> spec = (root, cq, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                for (String t : tokens) {
                    if (t.isBlank()) continue;
                    String like = "%" + t.replace("%", "").replace("_", "") + "%";
                    ps.add(cb.or(cb.like(cb.lower(root.<String>get("firstName")), like),
                                 cb.like(cb.lower(root.<String>get("lastName")), like)));
                }
                return cb.and(ps.toArray(new Predicate[0]));
            };
            result = repo.findAll(spec, pr.withSort(Sort.by("lastName", "firstName")));
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
            m.put("patient", Map.of("id", p.id, "fullName", p.getFullName(), "docNumber", p.docNumber));
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

    private Patient create(PatientValidator.Input in, boolean demo) {
        Map<String, String> errors = PatientValidator.validate(in, Dates.today());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        }
        String type = in.docType().trim().toUpperCase();
        String doc = PatientValidator.normalizeDoc(in.docNumber());
        repo.findByDocTypeAndDocNumber(type, doc).ifPresent(existing -> {
            throw ApiException.conflict("PACIENTE_DUPLICADO",
                    "Ya existe un paciente con " + type + " " + doc + ": " + existing.getFullName() + ".",
                    Map.of("docNumber", "Documento ya registrado.", "existingId", String.valueOf(existing.id)));
        });
        Patient p = new Patient();
        apply(p, in);
        p.docType = type;
        p.docNumber = doc;
        p.createdAt = Dates.now();
        p.createdBy = CurrentUser.usernameOrSystem();
        p.demo = demo;
        repo.save(p);
        audit.record("PACIENTE", p.id, "CREAR", p.getFullName() + " · " + type + " " + doc);
        events.publishEvent(new ContactSync.PatientSaved(p.id));
        return p;
    }

    /** HU-03: actualiza datos guardando fecha y usuario de modificación. El documento no se edita. */
    @Transactional
    public Patient update(Long id, PatientValidator.Input in) {
        Patient p = get(id);
        PatientValidator.Input merged = new PatientValidator.Input(p.docType, p.docNumber, in.firstName(), in.lastName(),
                in.birthDate(), in.sex(), in.phone(), in.email(), in.address(), in.district(),
                in.emergencyContactName(), in.emergencyContactPhone(), in.notes());
        Map<String, String> errors = PatientValidator.validate(merged, Dates.today());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los campos marcados.", errors);
        }
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(p.phone, PatientValidator.normalizePhone(in.phone()))) changes.add("teléfono");
        if (!Objects.equals(p.email, blankToNull(in.email()))) changes.add("correo");
        if (!Objects.equals(p.address, blankToNull(in.address()))) changes.add("dirección");
        apply(p, merged);
        p.updatedAt = Dates.now();
        p.updatedBy = CurrentUser.usernameOrSystem();
        repo.save(p);
        audit.record("PACIENTE", p.id, "EDITAR", changes.isEmpty() ? "Datos actualizados" : "Actualizó " + String.join(", ", changes));
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
        p.emergencyContactName = blankToNull(in.emergencyContactName());
        p.emergencyContactPhone = blankToNull(in.emergencyContactPhone()) == null ? null
                : PatientValidator.normalizePhone(in.emergencyContactPhone());
        p.notes = blankToNull(in.notes());
    }
}
