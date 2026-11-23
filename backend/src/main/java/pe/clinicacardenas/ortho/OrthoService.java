package pe.clinicacardenas.ortho;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.files.FileService;
import pe.clinicacardenas.files.PatientFile;
import pe.clinicacardenas.security.CurrentUser;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * v2.8: ortodoncia. Caso (aparato, diagnóstico, inicio y duración estimada), controles con arcos y elásticos,
 * y álbumes de fotos por etapa y vista. Las fotos se suben como archivos del paciente (FOTO o RADIOGRAFIA) o se
 * toman de los que ya tiene, y aquí solo se clasifican.
 */
@Service
public class OrthoService {
    private static final String ETAPA_NO_VALIDA = "Etapa no válida.";
    private static final String HISTORIA = "HISTORIA";
    private static final String MAXIMO_1000_CARACTERES = "Máximo 1000 caracteres.";
    private static final String ORTODONCIA = "ORTODONCIA";
    private static final String RETENCION = "RETENCION";
    private static final String VISTA_NO_VALIDA = "Vista no válida.";
    private static final String APPLIANCE = "appliance";
    private static final String CONTROLDATE = "controlDate";
    private static final String CURRENTSTAGE = "currentStage";
    private static final String DIAGNOSIS = "diagnosis";
    private static final String ENDDATE = "endDate";
    private static final String FILEID = "fileId";
    private static final String OBJECTIVES = "objectives";
    private static final String PATIENTID = "patientId";
    private static final String PHOTOS = "photos";
    private static final String STAGE = "stage";
    private static final String STARTDATE = "startDate";
    private static final String STATUS = "status";


    public static final Map<String, String> APPLIANCES = ordered(
            "BRACKETS_METALICOS", "Brackets metálicos", "BRACKETS_ESTETICOS", "Brackets estéticos (cerámicos o zafiro)",
            "AUTOLIGADO", "Brackets de autoligado", "ALINEADORES", "Alineadores transparentes", "REMOVIBLE", "Aparato removible", "OTRO", "Otro");
    public static final Map<String, String> STAGES = ordered(
            "INICIAL", "Registros iniciales", "ALINEACION", "Alineación y nivelación", "CIERRE", "Cierre de espacios",
            "DETALLADO", "Detallado y finalización", "RETIRO", "Retiro de aparatos", RETENCION, "Retención");
    public static final Map<String, String> VIEWS = ordered(
            "FRONTAL", "Frontal en reposo", "SONRISA", "Frontal sonriendo", "PERFIL", "Perfil",
            "INTRA_FRONTAL", "Intraoral frontal", "INTRA_DER", "Intraoral lateral derecha", "INTRA_IZQ", "Intraoral lateral izquierda",
            "OCLUSAL_SUP", "Oclusal superior", "OCLUSAL_INF", "Oclusal inferior",
            "PANORAMICA", "Radiografía panorámica", "CEFALOMETRIA", "Radiografía cefalométrica", "OTRA", "Otra");
    public static final Map<String, String> CLASSES = ordered("I", "Clase I", "II_1", "Clase II división 1", "II_2", "Clase II división 2", "III", "Clase III");
    public static final Map<String, String> STATUSES = ordered("ACTIVO", "En tratamiento", RETENCION, "En retención", "FINALIZADO", "Finalizado", "ABANDONADO", "Abandonó");

    private final JdbcTemplate jdbc;
    private final FileService files;
    private final AuditService audit;

    public OrthoService(JdbcTemplate jdbc, FileService files, AuditService audit) {
        this.jdbc = jdbc;
        this.files = files;
        this.audit = audit;
    }

    public record CaseRequest(Long dentistId, String appliance, String angleClass, String diagnosis, String objectives,
                              LocalDate startDate, Integer estimatedMonths, String status, String currentStage, Long planId) {}

    public record ControlRequest(LocalDate controlDate, String stage, String upperWire, String lowerWire, String elastics,
                                 String hygiene, String notes, Integer nextWeeks) {}

    public record LinkRequest(Long fileId, String stage, String view, LocalDate takenOn) {}

    public Map<String, Object> options() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("appliances", APPLIANCES);
        o.put("stages", STAGES);
        o.put("views", VIEWS);
        o.put("classes", CLASSES);
        o.put("statuses", STATUSES);
        return o;
    }

    // ------------------------------------------------------------------ casos

    /** Casos del paciente (el activo primero) con controles y fotos. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forPatient(Long patientId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : jdbc.queryForList("select id from ortho_cases where patient_id = ? order by status in ('ACTIVO', 'RETENCION') desc, start_date desc", Long.class, patientId))
            out.add(get(id));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("""
                select c.*, concat(p.first_name, ' ', p.last_name) as patient_name, du.full_name as dentist_name
                  from ortho_cases c join patients p on p.id = c.patient_id left join dentists d on d.id = c.dentist_id left join users du on du.id = d.user_id where c.id = ?
                """, id);
        if (l.isEmpty()) throw ApiException.notFound("El caso de ortodoncia");
        Map<String, Object> r = l.get(0);
        Map<String, Object> m = card(r);
        m.put("controls", jdbc.queryForList("""
                select id, control_date as controlDate, stage, upper_wire as upperWire, lower_wire as lowerWire, elastics, hygiene, notes,
                       next_weeks as nextWeeks, created_by as createdBy from ortho_controls where case_id = ? order by control_date desc, id desc
                """, id));
        m.put(PHOTOS, jdbc.queryForList("""
                select o.id, o.file_id as fileId, o.stage, o.view_code as view, o.taken_on as takenOn, f.title, f.content_type as contentType
                  from ortho_photos o join patient_files f on f.id = o.file_id
                 where o.case_id = ? and f.deleted_at is null order by o.taken_on, o.id
                """, id));
        return m;
    }

    private Map<String, Object> card(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put(PATIENTID, r.get("patient_id"));
        m.put("patientName", r.get("patient_name"));
        m.put("dentistId", r.get("dentist_id"));
        m.put("dentistName", r.get("dentist_name"));
        m.put(APPLIANCE, r.get(APPLIANCE));
        m.put("applianceLabel", APPLIANCES.get(r.get(APPLIANCE)));
        m.put("angleClass", r.get("angle_class"));
        m.put(DIAGNOSIS, r.get(DIAGNOSIS));
        m.put(OBJECTIVES, r.get(OBJECTIVES));
        LocalDate start = Dates.toLocalDate(r.get("start_date"));
        m.put(STARTDATE, start);
        m.put("estimatedMonths", r.get("estimated_months"));
        m.put(STATUS, r.get(STATUS));
        m.put("statusLabel", STATUSES.get(r.get(STATUS)));
        m.put(CURRENTSTAGE, r.get("current_stage"));
        m.put("currentStageLabel", STAGES.get(r.get("current_stage")));
        LocalDate end = r.get("end_date") == null ? null : Dates.toLocalDate(r.get("end_date"));
        m.put(ENDDATE, end);
        m.put("planId", r.get("plan_id"));
        long months = ChronoUnit.MONTHS.between(start, end != null ? end : Dates.today());
        m.put("monthsElapsed", Math.max(0, months));
        Object est = r.get("estimated_months");
        m.put("progress", est == null || ((Number) est).intValue() == 0 ? null : Math.min(100, Math.round(months * 100.0 / ((Number) est).intValue())));
        m.put("createdBy", r.get("created_by"));
        m.put("createdAt", r.get("created_at"));
        return m;
    }

    /** Casos activos o en retención de la clínica, con su último control y su próxima cita. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> active(Long dentistId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select c.*, concat(p.first_name, ' ', p.last_name) as patient_name, du.full_name as dentist_name,
                       (select max(control_date) from ortho_controls oc where oc.case_id = c.id) as last_control,
                       (select max(control_date + interval coalesce(next_weeks, 4) week) from ortho_controls oc where oc.case_id = c.id
                          and oc.control_date = (select max(x.control_date) from ortho_controls x where x.case_id = c.id)) as due_control,
                       (select min(a.start_at) from appointments a where a.patient_id = c.patient_id and a.start_at > ?
                          and a.status in ('PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION')) as next_appt,
                       (select count(*) from ortho_photos ph where ph.case_id = c.id) as photos
                  from ortho_cases c join patients p on p.id = c.patient_id left join dentists d on d.id = c.dentist_id left join users du on du.id = d.user_id
                 where c.status in ('ACTIVO', 'RETENCION') and (? is null or c.dentist_id = ?)
                 order by p.last_name, p.first_name
                """, Dates.now(), dentistId, dentistId)) {
            Map<String, Object> m = card(r);
            m.put("lastControl", r.get("last_control"));
            Object due = r.get("due_control");
            LocalDate d = due == null ? null : Dates.toLocalDate(due);
            m.put("dueControl", d);
            m.put("controlOverdue", d != null && d.isBefore(Dates.today()) && r.get("next_appt") == null);
            m.put("nextAppointment", r.get("next_appt"));
            m.put(PHOTOS, r.get(PHOTOS));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> create(Long patientId, CaseRequest r) {
        if (jdbc.queryForObject("select count(*) from patients where id = ? and active = true", Long.class, patientId) == 0)
            throw ApiException.notFound("El paciente");
        if (jdbc.queryForObject("select count(*) from ortho_cases where patient_id = ? and status in ('ACTIVO', 'RETENCION')", Long.class, patientId) > 0)
            throw ApiException.conflict("CASO_ABIERTO", "El paciente ya tiene un tratamiento de ortodoncia en curso.");
        Clean c = validate(r, patientId);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into ortho_cases (patient_id, dentist_id, appliance, angle_class, diagnosis, objectives, start_date, estimated_months,
                                             status, current_stage, plan_id, created_by, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO', ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, patientId);
            ps.setObject(2, c.dentistId);
            ps.setString(3, c.appliance);
            ps.setString(4, c.angleClass);
            ps.setString(5, c.diagnosis);
            ps.setString(6, c.objectives);
            ps.setObject(7, c.startDate);
            ps.setObject(8, c.estimatedMonths);
            ps.setString(9, c.stage);
            ps.setObject(10, c.planId);
            ps.setString(11, CurrentUser.usernameOrSystem());
            ps.setObject(12, Dates.now());
            return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        audit.register(HISTORIA, patientId, ORTODONCIA, "Inició caso de ortodoncia: " + APPLIANCES.get(c.appliance));
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, CaseRequest r) {
        Map<String, Object> cur = get(id);
        Long patientId = ((Number) cur.get(PATIENTID)).longValue();
        Clean c = validate(r, patientId);
        String status = r.status() == null || r.status().isBlank() ? (String) cur.get(STATUS) : r.status().trim().toUpperCase();
        if (!STATUSES.containsKey(status)) throw ApiException.field(STATUS, "Estado no válido.");
        boolean closing = Set.of("FINALIZADO", "ABANDONADO").contains(status);
        jdbc.update("""
                update ortho_cases set dentist_id = ?, appliance = ?, angle_class = ?, diagnosis = ?, objectives = ?, start_date = ?,
                       estimated_months = ?, status = ?, current_stage = ?, plan_id = ?, end_date = ?, updated_at = ? where id = ?
                """, c.dentistId, c.appliance, c.angleClass, c.diagnosis, c.objectives, c.startDate, c.estimatedMonths, status, c.stage, c.planId,
                closing ? endDateOr(cur.get(ENDDATE)) : null, Dates.now(), id);
        if (!status.equals(cur.get(STATUS)) || !c.stage.equals(cur.get(CURRENTSTAGE)))
            audit.register(HISTORIA, patientId, ORTODONCIA, "Caso de ortodoncia: " + STATUSES.get(status) + " · etapa " + STAGES.get(c.stage));
        return get(id);
    }

    @Transactional
    public Map<String, Object> addControl(Long id, ControlRequest r) {
        Map<String, Object> cur = get(id);
        if (!Set.of("ACTIVO", RETENCION).contains(cur.get(STATUS))) throw ApiException.conflict("CASO_CERRADO", "El caso está cerrado.");
        Map<String, String> e = new LinkedHashMap<>();
        if (r.controlDate() == null) e.put(CONTROLDATE, "Indica la fecha del control.");
        else if (r.controlDate().isAfter(Dates.today())) e.put(CONTROLDATE, "El control no puede tener fecha futura.");
        else if (r.controlDate().isBefore((LocalDate) cur.get(STARTDATE))) e.put(CONTROLDATE, "Es anterior al inicio del tratamiento.");
        String stage = r.stage() == null || r.stage().isBlank() ? (String) cur.get(CURRENTSTAGE) : r.stage().trim().toUpperCase();
        if (!STAGES.containsKey(stage)) e.put(STAGE, ETAPA_NO_VALIDA);
        String hy = r.hygiene() == null || r.hygiene().isBlank() ? null : r.hygiene().trim().toUpperCase();
        if (hy != null && !Set.of("BUENA", "REGULAR", "MALA").contains(hy)) e.put("hygiene", "Higiene no válida.");
        if (r.nextWeeks() != null && (r.nextWeeks() < 1 || r.nextWeeks() > 26)) e.put("nextWeeks", "Entre 1 y 26 semanas.");
        if (len(r.notes()) > 1000) e.put("notes", MAXIMO_1000_CARACTERES);
        if (len(r.upperWire()) > 60 || len(r.lowerWire()) > 60) e.put("upperWire", "Máximo 60 caracteres.");
        if (len(r.elastics()) > 100) e.put("elastics", "Máximo 100 caracteres.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del control.", e);
        jdbc.update("""
                insert into ortho_controls (case_id, control_date, stage, upper_wire, lower_wire, elastics, hygiene, notes, next_weeks, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, r.controlDate(), stage, blank(r.upperWire()), blank(r.lowerWire()), blank(r.elastics()), hy, blank(r.notes()),
                r.nextWeeks(), CurrentUser.usernameOrSystem(), Dates.now());
        if (!stage.equals(cur.get(CURRENTSTAGE))) jdbc.update("update ortho_cases set current_stage = ?, updated_at = ? where id = ?", stage, Dates.now(), id);
        audit.register(HISTORIA, ((Number) cur.get(PATIENTID)).longValue(), ORTODONCIA, "Control de ortodoncia del " + r.controlDate()
                + (blank(r.upperWire()) != null ? " · arco sup. " + r.upperWire().trim() : ""));
        return get(id);
    }

    // ------------------------------------------------------------------ fotos

    /** Sube una foto o radiografía como archivo del paciente y la ubica en el álbum de la etapa. */
    @Transactional
    public Map<String, Object> upload(Long id, MultipartFile file, String stage, String view, LocalDate takenOn) {
        Map<String, Object> cur = get(id);
        String st = code(stage, STAGES, STAGE, ETAPA_NO_VALIDA);
        String vw = code(view, VIEWS, "view", VISTA_NO_VALIDA);
        String kind = Set.of("PANORAMICA", "CEFALOMETRIA").contains(vw) ? "RADIOGRAFIA" : "FOTO";
        Long patientId = ((Number) cur.get(PATIENTID)).longValue();
        String contentType = file == null ? null : file.getContentType();
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.startsWith("image/")) throw ApiException.field("file", "Sube una foto (JPG, PNG o WEBP).");
        PatientFile f = files.upload(patientId, file, kind, "Ortodoncia · " + STAGES.get(st) + " · " + VIEWS.get(vw), null, null, false);
        link(id, f.id, st, vw, takenOn == null ? Dates.today() : takenOn);
        return get(id);
    }

    /** Agrega al álbum una foto que el paciente ya tenía en sus archivos. */
    @Transactional
    public Map<String, Object> linkExisting(Long id, LinkRequest r) {
        Map<String, Object> cur = get(id);
        String st = code(r.stage(), STAGES, STAGE, ETAPA_NO_VALIDA);
        String vw = code(r.view(), VIEWS, "view", VISTA_NO_VALIDA);
        if (r.fileId() == null) throw ApiException.field(FILEID, "Elige la foto.");
        PatientFile f = files.get(r.fileId());
        if (!f.patientId.equals(((Number) cur.get(PATIENTID)).longValue())) throw ApiException.field(FILEID, "La foto no es de este paciente.");
        if (!f.contentType.startsWith("image/")) throw ApiException.field(FILEID, "Solo se pueden agregar imágenes.");
        if (jdbc.queryForObject("select count(*) from ortho_photos where case_id = ? and file_id = ?", Long.class, id, f.id) > 0)
            throw ApiException.conflict("YA_EN_ALBUM", "Esa foto ya está en el álbum.");
        link(id, f.id, st, vw, r.takenOn() == null ? f.createdAt.toLocalDate() : r.takenOn());
        return get(id);
    }

    @Transactional
    public Map<String, Object> movePhoto(Long id, Long photoId, LinkRequest r) {
        get(id);
        String st = code(r.stage(), STAGES, STAGE, ETAPA_NO_VALIDA);
        String vw = code(r.view(), VIEWS, "view", VISTA_NO_VALIDA);
        if (jdbc.update("update ortho_photos set stage = ?, view_code = ?, taken_on = coalesce(?, taken_on) where id = ? and case_id = ?", st, vw, r.takenOn(), photoId, id) == 0)
            throw ApiException.notFound("La foto");
        return get(id);
    }

    /** Quita la foto del álbum (el archivo sigue en la ficha del paciente). */
    @Transactional
    public Map<String, Object> unlink(Long id, Long photoId) {
        get(id);
        if (jdbc.update("delete from ortho_photos where id = ? and case_id = ?", photoId, id) == 0) throw ApiException.notFound("La foto");
        return get(id);
    }

    private void link(Long caseId, Long fileId, String stage, String view, LocalDate takenOn) {
        if (takenOn.isAfter(Dates.today())) throw ApiException.field("takenOn", "La fecha de la foto no puede ser futura.");
        jdbc.update("insert into ortho_photos (case_id, file_id, stage, view_code, taken_on, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                caseId, fileId, stage, view, takenOn, CurrentUser.usernameOrSystem(), Dates.now());
    }

    // ------------------------------------------------------------------ validación

    private record Clean(Long dentistId, String appliance, String angleClass, String diagnosis, String objectives, LocalDate startDate,
                         Integer estimatedMonths, String stage, Long planId) {}

    private Clean validate(CaseRequest r, Long patientId) {
        Map<String, String> e = new LinkedHashMap<>();
        String ap = r.appliance() == null ? "" : r.appliance().trim().toUpperCase();
        if (!APPLIANCES.containsKey(ap)) e.put(APPLIANCE, "Elige el tipo de aparato.");
        String ac = r.angleClass() == null || r.angleClass().isBlank() ? null : r.angleClass().trim().toUpperCase();
        if (ac != null && !CLASSES.containsKey(ac)) e.put("angleClass", "Clase no válida.");
        if (r.startDate() == null) e.put(STARTDATE, "Indica la fecha de inicio.");
        else if (r.startDate().isAfter(Dates.today().plusMonths(3))) e.put(STARTDATE, "Máximo 3 meses hacia adelante.");
        if (r.estimatedMonths() != null && (r.estimatedMonths() < 1 || r.estimatedMonths() > 60)) e.put("estimatedMonths", "Entre 1 y 60 meses.");
        if (len(r.diagnosis()) > 1000) e.put(DIAGNOSIS, MAXIMO_1000_CARACTERES);
        if (len(r.objectives()) > 1000) e.put(OBJECTIVES, MAXIMO_1000_CARACTERES);
        String st = r.currentStage() == null || r.currentStage().isBlank() ? "INICIAL" : r.currentStage().trim().toUpperCase();
        if (!STAGES.containsKey(st)) e.put(CURRENTSTAGE, ETAPA_NO_VALIDA);
        if (r.dentistId() != null && jdbc.queryForObject("select count(*) from dentists where id = ?", Long.class, r.dentistId()) == 0)
            e.put("dentistId", "Odontólogo no válido.");
        if (r.planId() != null && jdbc.queryForObject("select count(*) from treatment_plans where id = ? and patient_id = ?", Long.class, r.planId(), patientId) == 0)
            e.put("planId", "El plan no es de este paciente.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del caso.", e);
        return new Clean(r.dentistId(), ap, ac, blank(r.diagnosis()), blank(r.objectives()), r.startDate(), r.estimatedMonths(), st, r.planId());
    }

    private static String code(String v, Map<String, String> allowed, String field, String msg) {
        String c = v == null ? "" : v.trim().toUpperCase();
        if (!allowed.containsKey(c)) throw ApiException.field(field, msg);
        return c;
    }

    private static int len(String s) { return s == null ? 0 : s.trim().length(); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);
    }

    private static Object endDateOr(Object current) { return current != null ? current : Dates.today(); }
}
