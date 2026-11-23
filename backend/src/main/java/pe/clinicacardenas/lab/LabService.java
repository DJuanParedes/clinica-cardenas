package pe.clinicacardenas.lab;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.billing.RucValidator;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * v2.5: órdenes de laboratorio dental. Cada trabajo (corona, prótesis, placa…) se envía a un laboratorio con fecha
 * de entrega, pasa por pruebas en boca y se entrega al paciente. Lo que se le paga al laboratorio se registra como
 * egreso de caja (categoría LABORATORIO), así el flujo de caja y el saldo por pagar siempre coinciden.
 *
 * Estados: POR_ENVIAR → ENVIADA ⇄ EN_PRUEBA → RECIBIDA → ENTREGADA. Cualquiera sin entregar puede ANULARSE.
 */
@Service
public class LabService {
    private static final String ANULADA = "ANULADA";
    private static final String ENTREGADA_2 = "ENTREGADA";
    private static final String ENVIADA_2 = "ENVIADA";
    private static final String EN_PRUEBA_2 = "EN_PRUEBA";
    private static final String LABORATORIO_2 = "LABORATORIO";
    private static final String POR_ENVIAR_2 = "POR_ENVIAR";
    private static final String RECIBIDA_2 = "RECIBIDA";
    private static final String BALANCE = "balance";
    private static final String LABORATORYID = "laboratoryId";
    private static final String PATIENTID = "patientId";
    private static final String STATUS = "status";
    private static final String TEETH = "teeth";
    private static final String TREATMENTITEMID = "treatmentItemId";
    private static final String WORKTYPE = "workType";


    private static final Map<String, String> WORK_TYPES_EDIT = new LinkedHashMap<>();
    public static final Map<String, String> WORK_TYPES = java.util.Collections.unmodifiableMap(WORK_TYPES_EDIT);
    static {
        WORK_TYPES_EDIT.put("CORONA", "Corona");
        WORK_TYPES_EDIT.put("PUENTE", "Puente fijo");
        WORK_TYPES_EDIT.put("INCRUSTACION", "Incrustación (inlay / onlay)");
        WORK_TYPES_EDIT.put("CARILLA", "Carillas");
        WORK_TYPES_EDIT.put("PERNO", "Perno muñón colado");
        WORK_TYPES_EDIT.put("PROTESIS_TOTAL", "Prótesis total");
        WORK_TYPES_EDIT.put("PROTESIS_PARCIAL", "Prótesis parcial removible");
        WORK_TYPES_EDIT.put("PROTESIS_IMPLANTE", "Prótesis sobre implante");
        WORK_TYPES_EDIT.put("PLACA", "Placa miorrelajante / de bruxismo");
        WORK_TYPES_EDIT.put("ORTODONCIA", "Aparato de ortodoncia / retenedor");
        WORK_TYPES_EDIT.put("ALINEADORES", "Alineadores");
        WORK_TYPES_EDIT.put("MODELOS", "Modelos de estudio / encerado");
        WORK_TYPES_EDIT.put("OTRO", "Otro trabajo");
    }

    public static final List<String> STATUSES = List.of(POR_ENVIAR_2, ENVIADA_2, EN_PRUEBA_2, RECIBIDA_2, ENTREGADA_2, ANULADA);
    private static final Map<String, Set<String>> NEXT = Map.of(
            POR_ENVIAR_2, Set.of(ENVIADA_2, ANULADA),
            ENVIADA_2, Set.of(EN_PRUEBA_2, RECIBIDA_2, ANULADA),
            EN_PRUEBA_2, Set.of(ENVIADA_2, RECIBIDA_2, ANULADA),
            RECIBIDA_2, Set.of(ENTREGADA_2, EN_PRUEBA_2, ANULADA),
            ENTREGADA_2, Set.of(),
            ANULADA, Set.of());

    private final JdbcTemplate jdbc;
    private final CashService cash;
    private final AuditService audit;

    public LabService(JdbcTemplate jdbc, CashService cash, AuditService audit) {
        this.jdbc = jdbc;
        this.cash = cash;
        this.audit = audit;
    }

    public record LabRequest(String name, String ruc, String phone, String email, String contact, String address, String notes, Boolean active) {}

    public record OrderRequest(Long patientId, Long laboratoryId, Long dentistId, Long planId, Long treatmentItemId, String workType,
                               String description, String teeth, String shade, String material, LocalDate dueDate,
                               BigDecimal cost, String notes, Boolean send) {}

    public record StatusRequest(String status, String note, LocalDate date) {}

    public record PaymentRequest(BigDecimal amount, String method, String reference, LocalDate date) {}

    // ------------------------------------------------------------------ laboratorios

    public List<Map<String, Object>> labs(boolean all) {
        return jdbc.queryForList("""
                select l.id, l.name, l.ruc, l.phone, l.email, l.contact, l.address, l.notes, l.active,
                       (select count(*) from lab_orders o where o.laboratory_id = l.id and o.status in ('ENVIADA','EN_PRUEBA')) as inProgress,
                       (select coalesce(sum(o.cost),0) - coalesce(sum((select coalesce(sum(p.amount),0) from lab_order_payments p join expenses x on x.id = p.expense_id
                                where p.order_id = o.id and x.status = 'VALIDO')),0)
                          from lab_orders o where o.laboratory_id = l.id and o.status <> 'ANULADA') as balance
                  from laboratories l""" + (all ? "" : " where l.active = true") + " order by l.active desc, l.name");
    }

    @Transactional
    public Map<String, Object> saveLab(Long id, LabRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = clean(r.name(), 120);
        if (name == null || name.length() < 3) e.put("name", "Escribe el nombre del laboratorio.");
        String ruc = clean(r.ruc(), 11);
        if (ruc != null && !RucValidator.isValid(ruc)) e.put("ruc", "El RUC no es válido.");
        String phone = clean(r.phone(), 15);
        if (phone != null && !phone.replaceAll("\\D", "").matches("^\\d{6,9}$")) e.put("phone", "Teléfono no válido.");
        String email = clean(r.email(), 120);
        if (email != null && !pe.clinicacardenas.common.Emails.valid(email)) e.put("email", "Correo no válido.");
        if (name != null) {
            Integer dup = jdbc.queryForObject("select count(*) from laboratories where lower(name) = lower(?) and id <> ?", Integer.class, name, id == null ? -1L : id);
            if (dup != null && dup > 0) e.put("name", "Ya existe un laboratorio con ese nombre.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del laboratorio.", e);
        boolean active = r.active() == null || r.active();
        if (id == null) {
            jdbc.update("insert into laboratories (name, ruc, phone, email, contact, address, notes, active, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?)",
                    name, ruc, phone, email, clean(r.contact(), 120), clean(r.address(), 200), clean(r.notes(), 300), active, Dates.now(), CurrentUser.usernameOrSystem());
            id = jdbc.queryForObject("select id from laboratories where name = ?", Long.class, name);
            audit.register(LABORATORIO_2, id, "CREAR", name);
        } else {
            int n = jdbc.update("update laboratories set name=?, ruc=?, phone=?, email=?, contact=?, address=?, notes=?, active=? where id=?",
                    name, ruc, phone, email, clean(r.contact(), 120), clean(r.address(), 200), clean(r.notes(), 300), active, id);
            if (n == 0) throw ApiException.notFound("El laboratorio");
            audit.register(LABORATORIO_2, id, "EDITAR", name + (active ? "" : " (inactivo)"));
        }
        Long fid = id;
        return labs(true).stream().filter(l -> fid.equals(((Number) l.get("id")).longValue())).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ órdenes

    private static final String ORDER_SELECT = """
            select o.id, concat('LAB-', lpad(o.id, 5, '0')) as code, o.patient_id as patientId,
                   concat(p.first_name, ' ', p.last_name) as patientName, p.hc_number as hcNumber, p.phone as patientPhone,
                   o.laboratory_id as laboratoryId, l.name as laboratoryName, l.phone as laboratoryPhone,
                   o.dentist_id as dentistId, u.full_name as dentistName, o.plan_id as planId, o.treatment_item_id as treatmentItemId,
                   o.work_type as workType, o.description, o.teeth, o.shade, o.material, o.status, o.sent_at as sentAt, o.due_date as dueDate,
                   o.received_at as receivedAt, o.delivered_at as deliveredAt, o.cost, o.notes, o.void_reason as voidReason,
                   o.created_at as createdAt, o.created_by as createdBy, o.updated_at as updatedAt,
                   (select coalesce(sum(pp.amount), 0) from lab_order_payments pp join expenses x on x.id = pp.expense_id
                     where pp.order_id = o.id and x.status = 'VALIDO') as paid
              from lab_orders o join patients p on p.id = o.patient_id join laboratories l on l.id = o.laboratory_id
              left join dentists d on d.id = o.dentist_id left join users u on u.id = d.user_id
            """;

    public Map<String, Object> list(String status, Long labId, Long patientId, boolean overdue, boolean unpaid, String q) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (patientId != null) { where.append(" and o.patient_id = ?"); args.add(patientId); }
        if (labId != null) { where.append(" and o.laboratory_id = ?"); args.add(labId); }
        if (status != null && !status.isBlank()) {
            if ("ACTIVAS".equals(status)) where.append(" and o.status in ('POR_ENVIAR','ENVIADA','EN_PRUEBA','RECIBIDA')");
            else if ("EN_LAB".equals(status)) where.append(" and o.status in ('ENVIADA','EN_PRUEBA')");
            else { where.append(" and o.status = ?"); args.add(status.trim().toUpperCase()); }
        }
        if (overdue) { where.append(" and o.status in ('ENVIADA','EN_PRUEBA') and o.due_date < ?"); args.add(Dates.today()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            where.append(" and (lower(concat(p.first_name, ' ', p.last_name)) like ? or p.doc_number like ? or lower(o.description) like ? or concat('lab-', lpad(o.id, 5, '0')) like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(ORDER_SELECT + where + " order by field(o.status,'ENVIADA','EN_PRUEBA','RECIBIDA','POR_ENVIAR','ENTREGADA','ANULADA'), o.due_date is null, o.due_date, o.id desc limit 300", args.toArray());
        rows.forEach(this::decorate);
        if (unpaid) rows = rows.stream().filter(r -> ((BigDecimal) r.get(BALANCE)).signum() > 0).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("summary", summary(patientId));
        return m;
    }

    public Map<String, Object> summary(Long patientId) {
        // Filtro por paciente siempre como parámetro enlazado (nunca concatenado en el SQL).
        String pf = patientId == null ? "" : " and o.patient_id = ?";
        Object[] pa = patientId == null ? new Object[0] : new Object[]{patientId};
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("porEnviar", count("o.status = 'POR_ENVIAR'" + pf, pa));
        s.put("enLaboratorio", count("o.status in ('ENVIADA','EN_PRUEBA')" + pf, pa));
        Object[] va = patientId == null ? new Object[]{Dates.today()} : new Object[]{Dates.today(), patientId};
        s.put("vencidas", jdbc.queryForObject("select count(*) from lab_orders o where o.status in ('ENVIADA','EN_PRUEBA') and o.due_date < ?" + pf, Integer.class, va));
        s.put("porEntregar", count("o.status = 'RECIBIDA'" + pf, pa));
        BigDecimal cost = jdbc.queryForObject("select coalesce(sum(o.cost),0) from lab_orders o where o.status <> 'ANULADA'" + pf, BigDecimal.class, pa);
        BigDecimal paid = jdbc.queryForObject("select coalesce(sum(pp.amount),0) from lab_order_payments pp join expenses x on x.id = pp.expense_id"
                + " join lab_orders o on o.id = pp.order_id where x.status = 'VALIDO' and o.status <> 'ANULADA'" + pf, BigDecimal.class, pa);
        s.put("porPagar", nz(cost).subtract(nz(paid)).max(BigDecimal.ZERO));
        return s;
    }

    private Integer count(String cond, Object... args) {
        return jdbc.queryForObject("select count(*) from lab_orders o where " + cond, Integer.class, args);
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> r = jdbc.queryForList(ORDER_SELECT + " where o.id = ?", id);
        if (r.isEmpty()) throw ApiException.notFound("La orden de laboratorio");
        Map<String, Object> m = decorate(r.get(0));
        m.put("events", jdbc.queryForList("select id, status, note, created_at as createdAt, created_by as createdBy from lab_order_events where order_id = ? order by id", id));
        m.put("payments", jdbc.queryForList("""
                select pp.id, pp.amount, x.id as expenseId, x.expense_date as date, x.method, x.reference, x.status, x.created_by as createdBy
                  from lab_order_payments pp join expenses x on x.id = pp.expense_id where pp.order_id = ? order by pp.id""", id));
        if (m.get(TREATMENTITEMID) != null) {
            m.put("treatmentItem", jdbc.queryForList("select ti.id, pr.name as `procedure`, ti.tooth, ti.status from treatment_items ti join procedures pr on pr.id = ti.procedure_id where ti.id = ?",
                    m.get(TREATMENTITEMID)).stream().findFirst().orElse(null));
        }
        return m;
    }

    private Map<String, Object> decorate(Map<String, Object> r) {
        BigDecimal cost = (BigDecimal) r.get("cost");
        BigDecimal paid = (BigDecimal) r.get("paid");
        r.put(BALANCE, ANULADA.equals(r.get(STATUS)) ? BigDecimal.ZERO : cost.subtract(paid).max(BigDecimal.ZERO));
        r.put("workTypeLabel", WORK_TYPES.getOrDefault((String) r.get(WORKTYPE), (String) r.get(WORKTYPE)));
        Object due = r.get("dueDate");
        LocalDate d = due instanceof java.sql.Date sd ? sd.toLocalDate() : due instanceof LocalDate ld ? ld : null;
        String st = (String) r.get(STATUS);
        boolean inLab = ENVIADA_2.equals(st) || EN_PRUEBA_2.equals(st);
        r.put("overdue", inLab && d != null && d.isBefore(Dates.today()));
        r.put("daysLeft", inLab && d != null ? java.time.temporal.ChronoUnit.DAYS.between(Dates.today(), d) : null);
        r.put("next", NEXT.getOrDefault(st, Set.of()).stream().sorted(Comparator.comparingInt(STATUSES::indexOf)).toList());
        return r;
    }

    @Transactional
    public Map<String, Object> create(OrderRequest r) {
        Values v = validate(r, null);
        boolean send = Boolean.TRUE.equals(r.send());
        String user = CurrentUser.usernameOrSystem();
        jdbc.update("""
                insert into lab_orders (patient_id, laboratory_id, dentist_id, plan_id, treatment_item_id, work_type, description, teeth, shade, material,
                  status, sent_at, due_date, cost, notes, created_at, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                r.patientId(), r.laboratoryId(), v.dentistId, v.planId, v.itemId, v.workType, v.description, v.teeth, v.shade, v.material,
                send ? ENVIADA_2 : POR_ENVIAR_2, send ? Dates.today() : null, r.dueDate(), v.cost, v.notes, Dates.now(), user);
        Long id = jdbc.queryForObject("select max(id) from lab_orders where created_by = ?", Long.class, user);
        event(id, send ? ENVIADA_2 : POR_ENVIAR_2, send ? "Orden creada y enviada al laboratorio" : "Orden creada");
        audit.register(LABORATORIO_2, id, "ORDEN", WORK_TYPES.get(v.workType) + " · " + v.description + " · S/ " + v.cost);
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, OrderRequest r) {
        Map<String, Object> o = get(id);
        String st = (String) o.get(STATUS);
        if (ENTREGADA_2.equals(st) || ANULADA.equals(st)) throw ApiException.badRequest("ORDEN_CERRADA", "La orden ya está " + st.toLowerCase() + ".");
        Values v = validate(new OrderRequest(((Number) o.get(PATIENTID)).longValue(), r.laboratoryId(), r.dentistId(), r.planId(), r.treatmentItemId(),
                r.workType(), r.description(), r.teeth(), r.shade(), r.material(), r.dueDate(), r.cost(), r.notes(), null), id);
        BigDecimal paid = (BigDecimal) o.get("paid");
        if (v.cost.compareTo(paid) < 0) throw ApiException.field("cost", "Ya se pagó S/ " + paid + ": el costo no puede ser menor.");
        jdbc.update("""
                update lab_orders set laboratory_id=?, dentist_id=?, plan_id=?, treatment_item_id=?, work_type=?, description=?, teeth=?, shade=?,
                  material=?, due_date=?, cost=?, notes=?, updated_at=?, updated_by=? where id=?""",
                r.laboratoryId(), v.dentistId, v.planId, v.itemId, v.workType, v.description, v.teeth, v.shade, v.material, r.dueDate(), v.cost, v.notes,
                Dates.now(), CurrentUser.usernameOrSystem(), id);
        audit.register(LABORATORIO_2, id, "EDITAR", v.description);
        return get(id);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, StatusRequest r) {
        Map<String, Object> o = get(id);
        String from = (String) o.get(STATUS);
        String to = r.status() == null ? "" : r.status().trim().toUpperCase();
        if (!NEXT.getOrDefault(from, Set.of()).contains(to)) {
            throw ApiException.badRequest("TRANSICION_INVALIDA", "No se puede pasar de " + label(from) + " a " + label(to) + ".");
        }
        String note = clean(r.note(), 300);
        LocalDate date = r.date() == null ? Dates.today() : r.date();
        if (date.isAfter(Dates.today())) throw ApiException.field("date", "La fecha no puede ser futura.");
        switch (to) {
            case ENVIADA_2 -> jdbc.update("update lab_orders set status=?, sent_at=coalesce(sent_at, ?) where id=?", to, date, id);
            case RECIBIDA_2 -> jdbc.update("update lab_orders set status=?, received_at=? where id=?", to, date, id);
            case ENTREGADA_2 -> jdbc.update("update lab_orders set status=?, delivered_at=? where id=?", to, date, id);
            case ANULADA -> {
                if (note == null) throw ApiException.field("note", "Indica el motivo de la anulación.");
                jdbc.update("update lab_orders set status=?, void_reason=? where id=?", to, note, id);
            }
            default -> jdbc.update("update lab_orders set status=? where id=?", to, id);
        }
        jdbc.update("update lab_orders set updated_at=?, updated_by=? where id=?", Dates.now(), CurrentUser.usernameOrSystem(), id);
        event(id, to, note);
        audit.register(LABORATORIO_2, id, "ESTADO", label(from) + " → " + label(to) + (note == null ? "" : " · " + note));
        return get(id);
    }

    /** Pago al laboratorio: se registra como egreso de caja (LABORATORIO) y queda ligado a la orden. */
    @Transactional
    public Map<String, Object> pay(Long id, PaymentRequest r) {
        Map<String, Object> o = get(id);
        if (ANULADA.equals(o.get(STATUS))) throw ApiException.badRequest("ORDEN_ANULADA", "La orden está anulada.");
        BigDecimal balance = (BigDecimal) o.get(BALANCE);
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.field("amount", "Ingresa el importe pagado.");
        BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(balance) > 0) throw ApiException.field("amount", "El saldo por pagar es S/ " + balance + ".");
        String concept = "Laboratorio " + o.get("code") + " · " + o.get("workTypeLabel") + (o.get(TEETH) == null ? "" : " " + o.get(TEETH))
                + " · " + o.get("patientName");
        var expense = cash.registerExpense(new CashService.ExpenseRequest(r.date(), LABORATORIO_2, concept.length() > 200 ? concept.substring(0, 200) : concept,
                amount, r.method(), r.reference(), (String) o.get("laboratoryName")));
        jdbc.update("insert into lab_order_payments (order_id, expense_id, amount) values (?,?,?)", id, expense.id, amount);
        event(id, (String) o.get(STATUS), "Pago al laboratorio S/ " + amount + " (" + r.method() + ")");
        return get(id);
    }

    // ------------------------------------------------------------------ utilidades

    private record Values(Long dentistId, Long planId, Long itemId, String workType, String description, String teeth, String shade,
                          String material, BigDecimal cost, String notes) {}

    private Values validate(OrderRequest r, Long id) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r.patientId() == null) e.put(PATIENTID, "Elige al paciente.");
        else {
            Integer c = jdbc.queryForObject("select count(*) from patients where id = ?", Integer.class, r.patientId());
            if (c == null || c == 0) e.put(PATIENTID, "Paciente no encontrado.");
        }
        if (r.laboratoryId() == null) e.put(LABORATORYID, "Elige el laboratorio.");
        else {
            List<Boolean> act = jdbc.queryForList("select active from laboratories where id = ?", Boolean.class, r.laboratoryId());
            if (act.isEmpty()) e.put(LABORATORYID, "Laboratorio no encontrado.");
            else if (id == null && !act.get(0)) e.put(LABORATORYID, "Ese laboratorio está inactivo.");
        }
        String type = r.workType() == null ? null : r.workType().trim().toUpperCase();
        if (type == null || !WORK_TYPES.containsKey(type)) e.put(WORKTYPE, "Elige el tipo de trabajo.");
        String desc = clean(r.description(), 300);
        if (desc == null || desc.length() < 3) e.put("description", "Describe el trabajo (ej.: corona de zirconio pieza 16).");
        String teeth = clean(r.teeth(), 60);
        if (teeth != null && !teeth.matches("^[0-9 ,\\-a-zA-ZáéíóúÁÉÍÓÚ]+$")) e.put(TEETH, "Escribe las piezas FDI separadas por coma (ej.: 16, 17).");
        BigDecimal cost = r.cost() == null ? BigDecimal.ZERO : r.cost().setScale(2, RoundingMode.HALF_UP);
        if (cost.signum() < 0 || cost.compareTo(new BigDecimal("50000")) > 0) e.put("cost", "Costo entre S/ 0 y S/ 50 000.");
        if (r.dueDate() != null && id == null && r.dueDate().isBefore(Dates.today())) e.put("dueDate", "La fecha de entrega no puede ser pasada.");
        Long dentist = r.dentistId();
        if (dentist == null) {
            dentist = jdbc.queryForList("select d.id from dentists d join users u on u.id = d.user_id where lower(u.username) = lower(?)", Long.class,
                    CurrentUser.usernameOrSystem()).stream().findFirst().orElse(null);
        }
        Long plan = r.planId();
        Long item = r.treatmentItemId();
        if (item != null && r.patientId() != null) {
            List<Long> p = jdbc.queryForList("select tp.id from treatment_items ti join treatment_plans tp on tp.id = ti.plan_id where ti.id = ? and tp.patient_id = ?",
                    Long.class, item, r.patientId());
            if (p.isEmpty()) e.put(TREATMENTITEMID, "Ese procedimiento no es del paciente.");
            else plan = p.get(0);
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos de la orden.", e);
        return new Values(dentist, plan, item, type, desc, teeth, clean(r.shade(), 20), clean(r.material(), 60), cost, clean(r.notes(), 500));
    }

    private void event(Long id, String status, String note) {
        jdbc.update("insert into lab_order_events (order_id, status, note, created_at, created_by) values (?,?,?,?,?)",
                id, status, note, Dates.now(), CurrentUser.usernameOrSystem());
    }

    public static String label(String s) {
        return switch (s == null ? "" : s) {
            case POR_ENVIAR_2 -> "Por enviar";
            case ENVIADA_2 -> "En laboratorio";
            case EN_PRUEBA_2 -> "En prueba";
            case RECIBIDA_2 -> "Recibida";
            case ENTREGADA_2 -> "Entregada";
            case ANULADA -> "Anulada";
            default -> s;
        };
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
