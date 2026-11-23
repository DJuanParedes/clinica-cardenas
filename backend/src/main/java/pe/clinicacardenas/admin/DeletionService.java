package pe.clinicacardenas.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * v2.11: eliminación definitiva de registros, solo para el administrador (permiso ELIMINAR_REGISTROS).
 *
 * <p>Antes de borrar se arma un plan recorriendo las llaves foráneas de la base: lo que depende del registro se borra
 * con él, las referencias opcionales se dejan en blanco y lo que la ley u otra regla obliga a conservar (comprobantes
 * enviados a SUNAT, Libro de Reclamaciones, consentimientos firmados, pagos vigentes, días de caja cerrados) impide el
 * borrado y se explica por qué. Todo lo borrado se guarda completo en deleted_records (la papelera).</p>
 */
@Service
public class DeletionService {
    private static final String IN = " in (";
    private static final String WHERE = " where ";
    private static final String APPOINTMENTS = "appointments";
    private static final String DENTISTS = "dentists";
    private static final String LAB_ORDERS = "lab_orders";
    private static final String PATIENTS = "patients";
    private static final String SELECT_COUNT_FROM = "select count(*) from ";
    private static final String T_NAME = "t.name";
    private static final String TREATMENT_ITEMS = "treatment_items";
    private static final String TREATMENT_PLANS = "treatment_plans";


    /** Una tabla que referencia a otra. */
    record Ref(String table, String column, boolean nullable) {}

    /** Datos que se conservan siempre: si alguno de estos registros depende de lo que se quiere borrar, no se borra. */
    record Keep(String where, String why) {}

    private static final Map<String, Keep> ALWAYS_KEEP = Map.of(
            "invoices", new Keep("1=1", "comprobantes electrónicos emitidos: ante SUNAT solo se anulan (nota de crédito o comunicación de baja), no se borran"),
            "sunat_submissions", new Keep("1=1", "envíos a SUNAT"),
            "complaints", new Keep("1=1", "hojas del Libro de Reclamaciones: la ley (D.S. 011-2011-PCM) obliga a conservarlas"),
            "consent_requests", new Keep("signed_at is not null", "consentimientos informados firmados: son parte de la historia clínica y deben conservarse"),
            "payments", new Keep("status = 'VALIDO' or date(paid_at) in (select day from cash_closings where status = 'CERRADA')",
                    "pagos vigentes o de días de caja ya cerrados: anúlalos primero en Caja (o reabre el día)"),
            "commission_lines", new Keep("settlement_id is not null", "comisiones ya liquidadas a un odontólogo"),
            "commission_payouts", new Keep("1=1", "pagos de comisiones"),
            "commission_settlements", new Keep("1=1", "liquidaciones de comisiones"),
            TREATMENT_ITEMS, new Keep("invoice_id is not null", "tratamientos ya facturados")
    );

    /**
     * Referencias opcionales donde dejar el valor en blanco cambiaría su sentido (una regla de comisión o promoción
     * sin procedimiento pasaría a valer para todos) o dejaría basura (un parentesco sin familiar, una tarea de un
     * paciente que ya no existe): esas filas se eliminan junto con el registro.
     */
    private static final Set<String> FORCE_CASCADE = Set.of(
            "commission_rules.procedure_id", "promotions.procedure_id", "patient_relations.related_patient_id", "tasks.patient_id");

    /** Registro que el administrador puede eliminar. */
    record Entity(String key, String title, String table, String pk, String labelSql, Set<String> blockedBy,
                  BiConsumer<Object, Plan> check) {}

    private static Entity e(String key, String title, String table, String labelSql, String... blockedBy) {
        return new Entity(key, title, table, "id", labelSql, Set.of(blockedBy), null);
    }

    private final Map<String, Entity> entities = new LinkedHashMap<>();
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper json;
    private final java.util.concurrent.atomic.AtomicReference<Map<String, List<Ref>>> refs = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicReference<Set<String>> tablesWithId = new java.util.concurrent.atomic.AtomicReference<>();

    private final org.springframework.context.ApplicationEventPublisher events;

    public DeletionService(JdbcTemplate jdbc, AuditService audit, ObjectMapper json, org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.jdbc = jdbc;
        this.audit = audit;
        this.json = json;
        for (Entity x : List.of(
                new Entity(PATIENTS, "Paciente", PATIENTS, "id", "concat_ws(' · ', concat(t.first_name, ' ', t.last_name), concat(t.doc_type, ' ', t.doc_number))", Set.of(), this::checkPatient),
                e(APPOINTMENTS, "Cita", APPOINTMENTS, "concat_ws(' · ', (select concat(p.first_name, ' ', p.last_name) from patients p where p.id = t.patient_id), date_format(t.start_at, '%d/%m/%Y %H:%i'), t.reason)"),
                new Entity("users", "Usuario", "users", "id", "concat(t.full_name, ' (', t.username, ')')", Set.of(DENTISTS), this::checkUser),
                e(DENTISTS, "Odontólogo", DENTISTS, "coalesce((select u.full_name from users u where u.id = t.user_id), concat('Odontólogo ', t.id))",
                        APPOINTMENTS, TREATMENT_PLANS, TREATMENT_ITEMS, "diagnoses", "ortho_cases", "commission_settlements"),
                e("procedures", "Procedimiento", "procedures", "concat_ws(' · ', t.code, t.name)", TREATMENT_ITEMS),
                e(TREATMENT_PLANS, "Plan de tratamiento", TREATMENT_PLANS, "concat_ws(' · ', t.title, (select concat(p.first_name, ' ', p.last_name) from patients p where p.id = t.patient_id))"),
                e(TREATMENT_ITEMS, "Tratamiento del plan", TREATMENT_ITEMS, "concat_ws(' · ', (select pr.name from procedures pr where pr.id = t.procedure_id), concat('pieza ', t.tooth))"),
                e("tasks", "Tarea", "tasks", "t.title"),
                e("leads", "Oportunidad (CRM)", "leads", "t.full_name"),
                e(LAB_ORDERS, "Orden de laboratorio", LAB_ORDERS, "concat_ws(' · ', t.work_type, t.description)"),
                e("laboratories", "Laboratorio", "laboratories", T_NAME, LAB_ORDERS),
                e("commission_rules", "Regla de comisión", "commission_rules", "concat_ws(' · ', coalesce(t.category, 'General'), t.calc_type, t.value)", "commission_lines"),
                e("consent_templates", "Plantilla de consentimiento", "consent_templates", T_NAME, "consent_requests"),
                e("anamnesis_templates", "Formulario de anamnesis", "anamnesis_templates", T_NAME, "anamnesis_records"),
                e("campaigns", "Campaña", "campaigns", T_NAME),
                e("segments", "Segmento", "segments", T_NAME),
                e("promotions", "Promoción", "promotions", T_NAME),
                e("inventory_items", "Material de inventario", "inventory_items", T_NAME),
                e("inventory_purchases", "Compra de inventario", "inventory_purchases", "concat_ws(' · ', t.supplier, t.doc_number, date_format(t.purchase_date, '%d/%m/%Y'))"),
                e("supply_requests", "Pedido de material", "supply_requests", "coalesce(t.item_text, concat('Pedido ', t.id))"),
                new Entity("expenses", "Egreso de caja", "expenses", "id", "concat_ws(' · ', t.concept, date_format(t.expense_date, '%d/%m/%Y'), concat('S/ ', t.amount))", Set.of(), this::checkExpense),
                e("waitlist", "Lista de espera", "waitlist", "coalesce((select concat(p.first_name, ' ', p.last_name) from patients p where p.id = t.patient_id), concat('Registro ', t.id))"),
                e("patient_notes", "Nota del paciente", "patient_notes", "left(t.text, 80)"),
                e("assistant_faq", "Pregunta frecuente del asistente", "assistant_faq", "left(t.question, 80)"),
                new Entity("invoice_series", "Serie de comprobantes", "invoice_series", "id", "concat(t.doc_type, ' ', t.series)", Set.of(), this::checkSeries)
        )) entities.put(x.key(), x);
    }

    // ------------------------------------------------------------------ esquema

    private void loadSchema() {
        if (refs.get() != null) return;
        Map<String, List<Ref>> m = new HashMap<>();
        jdbc.query("""
                select k.referenced_table_name, k.table_name, k.column_name, c.is_nullable
                  from information_schema.key_column_usage k
                  join information_schema.columns c on c.table_schema = k.table_schema and c.table_name = k.table_name and c.column_name = k.column_name
                 where k.table_schema = database() and k.referenced_table_name is not null
                """, rs -> {
            m.computeIfAbsent(rs.getString(1), x -> new ArrayList<>())
                    .add(new Ref(rs.getString(2), rs.getString(3), "YES".equals(rs.getString(4))));
        });
        tablesWithId.set(new HashSet<>(jdbc.queryForList(
                "select table_name from information_schema.columns where table_schema = database() and column_name = 'id'", String.class)));
        refs.set(m);
    }

    // ------------------------------------------------------------------ plan

    /** Lo que pasaría al eliminar: qué se borra, qué queda sin referencia y qué lo impide. */
    public static class Plan {
        final List<String[]> deletes = new ArrayList<>();      // tabla, where, parámetros (ids separados por coma)
        final List<String[]> nulls = new ArrayList<>();        // tabla, columna, where, ids
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, Long> unlinked = new LinkedHashMap<>();
        final List<String> blockers = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        public void block(String m) { if (!blockers.contains(m)) blockers.add(m); }
        public void warn(String m) { if (!warnings.contains(m)) warnings.add(m); }
    }

    private Entity entity(String key) {
        Entity x = entities.get(key);
        if (x == null) throw ApiException.notFound("El tipo de registro");
        return x;
    }

    private Object idValue(Entity x, String id) {
        if (!"id".equals(x.pk())) return id;
        try { return Long.parseLong(id); } catch (NumberFormatException e) { throw ApiException.notFound(x.title()); }
    }

    private String labelOf(Entity x, Object id) {
        List<String> l = jdbc.queryForList("select " + x.labelSql() + " from " + x.table() + " t where t." + x.pk() + " = ?", String.class, id);
        if (l.isEmpty()) throw ApiException.notFound("El registro");
        return l.get(0) == null ? x.title() + " " + id : l.get(0);
    }

    private static String in(List<?> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private Plan plan(Entity x, Object id) {
        loadSchema();
        Plan p = new Plan();
        if (x.check() != null) x.check().accept(id, p);
        Keep own = ALWAYS_KEEP.get(x.table());
        if (own != null && count(SELECT_COUNT_FROM + x.table() + WHERE + x.pk() + " = ? and (" + own.where() + ")", id) > 0) {
            p.block("No se puede: son " + own.why() + ".");
        }
        walk(x.table(), List.of(id), x.blockedBy(), p, new HashSet<>(), 0);
        p.deletes.add(new String[]{x.table(), x.pk() + " = ?", String.valueOf(id)});
        return p;
    }

    private void walk(String table, List<?> ids, Set<String> blockedBy, Plan p, Set<String> seen, int depth) {
        if (ids.isEmpty() || depth > 6) return;
        for (Ref r : refs.get().getOrDefault(table, List.of())) {
            String key = r.table() + "." + r.column() + ":" + ids;
            if (!seen.add(key)) continue;
            Object[] args = ids.toArray();
            long n = count(SELECT_COUNT_FROM + r.table() + WHERE + r.column() + IN + in(ids) + ")", args);
            if (n == 0) continue;
            String name = TableNames.of(r.table());
            if (depth == 0 && blockedBy.contains(r.table())) {
                p.block("Está vinculado a " + n + " " + name + ". Elimínalos o cámbialos primero.");
                continue;
            }
            Keep keep = ALWAYS_KEEP.get(r.table());
            String extra = "";
            if (keep != null) {
                long kept = count(SELECT_COUNT_FROM + r.table() + WHERE + r.column() + IN + in(ids) + ") and (" + keep.where() + ")", args);
                if (kept > 0) {
                    p.block(kept + " " + keep.why() + ".");
                    if ("1=1".equals(keep.where())) continue;
                }
                extra = " and not (" + keep.where() + ")";
            }
            String where = r.column() + IN + in(ids) + ")" + extra;
            if (r.nullable() && !FORCE_CASCADE.contains(r.table() + "." + r.column())) {
                long m = count(SELECT_COUNT_FROM + r.table() + WHERE + where, args);
                if (m == 0) continue;
                p.nulls.add(new String[]{r.table(), r.column(), where, join(ids)});
                p.unlinked.merge(name, m, Long::sum);
                continue;
            }
            if (tablesWithId.get().contains(r.table())) {
                List<Long> child = jdbc.queryForList("select id from " + r.table() + WHERE + where, Long.class, args);
                if (child.isEmpty()) continue;
                walk(r.table(), child, Set.of(), p, seen, depth + 1);
                p.deletes.add(new String[]{r.table(), "id in (" + in(child) + ")", join(child)});
                p.counts.merge(name, (long) child.size(), Long::sum);
            } else {
                p.deletes.add(new String[]{r.table(), where, join(ids)});
                p.counts.merge(name, n, Long::sum);
            }
        }
    }

    private static String join(List<?> ids) {
        StringJoiner j = new StringJoiner(",");
        ids.forEach(i -> j.add(String.valueOf(i)));
        return j.toString();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Object[] params(String csv) {
        return Arrays.stream(csv.split(",")).map(s -> s.matches("-?\\d+") ? (Object) Long.parseLong(s) : s).toArray();
    }

    // ------------------------------------------------------------------ reglas propias

    private void checkPatient(Object id, Plan p) {
        long clinical = count("""
                select (select count(*) from treatment_plans where patient_id = ?) + (select count(*) from odontogram_entries where patient_id = ?)
                     + (select count(*) from clinical_histories where patient_id = ?) + (select count(*) from diagnoses where patient_id = ?)
                     + (select count(*) from anamnesis_records where patient_id = ?)
                """, id, id, id, id, id);
        if (clinical > 0) {
            p.warn("Este paciente tiene historia clínica. La norma (NTS 139-MINSA) obliga a conservarla: elimina solo pacientes de prueba o duplicados. Igual queda una copia completa en la papelera.");
        }
        long files = count("select count(*) from patient_files where patient_id = ? and deleted_at is null", id);
        if (files > 0) p.warn("Sus " + files + " archivo(s) dejarán de verse en el sistema.");
    }

    private void checkUser(Object id, Plan p) {
        Map<String, Object> u = jdbc.queryForMap("select username, role_code from users where id = ?", id);
        if (u.get("username").equals(CurrentUser.usernameOrSystem())) p.block("No puedes eliminar tu propia cuenta.");
        if ("ADMIN".equals(u.get("role_code"))
                && count("select count(*) from users where role_code = 'ADMIN' and active = true and id <> ?", id) == 0) {
            p.block("Es el único administrador activo: crea otro administrador antes de eliminarlo.");
        }
        p.warn("Si solo quieres quitarle el acceso, es mejor desactivarlo: así su nombre se sigue viendo en el historial.");
    }

    private void checkExpense(Object id, Plan p) {
        if (count("select count(*) from expenses e join cash_closings c on c.day = e.expense_date and c.status = 'CERRADA' where e.id = ?", id) > 0) {
            p.block("Es de un día de caja ya cerrado. Reabre el día en Cierre de caja para poder eliminarlo.");
        }
        if (count("select count(*) from inventory_purchases where expense_id = ?", id) > 0) {
            p.warn("Está ligado a una compra de inventario; la compra quedará sin egreso.");
        }
    }

    private void checkSeries(Object id, Plan p) {
        if (count("select count(*) from invoice_series where id = ? and last_number > 0", id) > 0) {
            p.block("Ya se emitieron comprobantes con esta serie: ante SUNAT no se puede borrar, solo desactivar.");
        }
    }

    // ------------------------------------------------------------------ API

    public List<Map<String, Object>> types() {
        List<Map<String, Object>> out = new ArrayList<>();
        entities.values().forEach(x -> out.add(Map.of("key", x.key(), "title", x.title())));
        return out;
    }

    public Map<String, Object> preview(String key, String rawId) {
        Entity x = entity(key);
        Object id = idValue(x, rawId);
        String label = labelOf(x, id);
        Plan p = plan(x, id);
        Object[] q = pendingMessages(p, x.table(), id);
        if (q != null) {
            long n = count("select count(*) from notifications where " + q[0], (Object[]) q[1]);
            if (n > 0) p.warn("Tiene " + n + " aviso(s) por enviar (recordatorios, confirmaciones): se anularán para que no le lleguen.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entity", x.key());
        m.put("title", x.title());
        m.put("id", rawId);
        m.put("label", label);
        m.put("alsoDeleted", p.counts);
        m.put("unlinked", p.unlinked);
        m.put("blockers", p.blockers);
        m.put("warnings", p.warnings);
        m.put("canDelete", p.blockers.isEmpty());
        return m;
    }

    public record Confirm(String confirm, String reason) {}

    @Transactional
    public Map<String, Object> delete(String key, String rawId, Confirm c) {
        Entity x = entity(key);
        if (c == null || !"ELIMINAR".equals(c.confirm() == null ? null : c.confirm().trim().toUpperCase())) {
            throw ApiException.field("confirm", "Escribe ELIMINAR para confirmar.");
        }
        Object id = idValue(x, rawId);
        String label = labelOf(x, id);
        Plan p = plan(x, id);
        if (!p.blockers.isEmpty()) throw ApiException.conflict("NO_SE_PUEDE_ELIMINAR", String.join(" ", p.blockers));

        // Copia completa antes de borrar.
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String[] d : p.deletes) {
            copy.computeIfAbsent(d[0], k -> new ArrayList<Map<String, Object>>());
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) copy.get(d[0]);
            rows.addAll(jdbc.queryForList("select * from " + d[0] + WHERE + d[1], params(d[2])));
        }
        Map<String, Object> nulled = new LinkedHashMap<>();
        for (String[] n : p.nulls) {
            nulled.put(n[0] + "." + n[1], n[3]);
            jdbc.update("update " + n[0] + " set " + n[1] + " = null where " + n[2], params(n[3]));
        }
        int cancelled = cancelPendingMessages(p, x.table(), id);
        for (String[] d : p.deletes) jdbc.update("delete from " + d[0] + WHERE + d[1], params(d[2]));
        if (cancelled > 0) events.publishEvent(new pe.clinicacardenas.notification.NotificationService.Queued());

        String summary = p.counts.isEmpty() ? "Solo el registro" : "Con él: " + p.counts.entrySet().stream()
                .map(en -> en.getValue() + " " + en.getKey()).reduce((a, b) -> a + ", " + b).orElse("");
        String data;
        try {
            data = json.writeValueAsString(Map.of("deleted", copy, "unlinked", nulled));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar la copia de lo eliminado", ex);
        }
        String reason = c.reason() == null || c.reason().isBlank() ? null : c.reason().trim();
        jdbc.update("insert into deleted_records (entity, entity_id, label, summary, data, reason, deleted_by, deleted_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                x.key(), rawId, cut(label, 200), cut(summary, 500), data, reason == null ? null : cut(reason, 300), CurrentUser.usernameOrSystem(), Dates.now());
        audit.register(x.title().toUpperCase(Locale.ROOT).replace(' ', '_'), "id".equals(x.pk()) ? (Long) id : null, "ELIMINAR",
                label + " · " + summary + (reason == null ? "" : " · motivo: " + reason));
        return Map.of("ok", true, "label", label, "summary", summary);
    }

    /**
     * Las citas y pacientes eliminados no deben seguir recibiendo recordatorios: se anulan los avisos pendientes y,
     * si el microservicio ya tenía uno programado, se le pide anularlo (igual que al cancelar una cita).
     */
    /** Condición y parámetros de los avisos pendientes de las citas/pacientes que se eliminarían; null si no hay. */
    private Object[] pendingMessages(Plan p, String rootTable, Object rootId) {
        List<Object> appts = new ArrayList<>();
        List<Object> patients = new ArrayList<>();
        for (String[] d : p.deletes) {
            if (d[0].equals(APPOINTMENTS)) appts.addAll(jdbc.queryForList("select id from appointments where " + d[1], Long.class, params(d[2])));
        }
        if (PATIENTS.equals(rootTable)) patients.add(rootId);
        if (appts.isEmpty() && patients.isEmpty()) return null;
        String where = "status in ('PENDIENTE','PROGRAMADO','FALLIDO') and operation <> 'ANULAR' and ("
                + (appts.isEmpty() ? "1=0" : "appointment_id in (" + in(appts) + ")") + " or "
                + (patients.isEmpty() ? "1=0" : "patient_id in (" + in(patients) + ")") + ")";
        List<Object> args = new ArrayList<>(appts);
        args.addAll(patients);
        return new Object[]{where, args.toArray()};
    }

    private int cancelPendingMessages(Plan p, String rootTable, Object rootId) {
        Object[] q = pendingMessages(p, rootTable, rootId);
        if (q == null) return 0;
        String where = (String) q[0];
        Object[] args = (Object[]) q[1];
        List<Map<String, Object>> rows = jdbc.queryForList("select id, appointment_id, channel, destination, idempotency_key, operation, status from notifications where " + where, args);
        for (Map<String, Object> n : rows) {
            jdbc.update("update notifications set status = 'ANULADO', updated_at = ? where id = ?", Dates.now(), n.get("id"));
            if ("PROGRAMADO".equals(n.get("status")) && "PROGRAMAR".equals(n.get("operation"))) {
                String key = "anular:" + n.get("idempotency_key");
                if (count("select count(*) from notifications where idempotency_key = ?", key) == 0) {
                    jdbc.update("""
                            insert into notifications (patient_id, appointment_id, type, operation, channel, destination, subject, body, idempotency_key, status, attempts, created_at)
                            values (null, ?, 'RECORDATORIO', 'ANULAR', ?, ?, 'Anular recordatorio', ?, ?, 'PENDIENTE', 0, ?)
                            """, n.get("appointment_id"), n.get("channel"), n.get("destination"), "Se anula el recordatorio " + n.get("idempotency_key") + " (registro eliminado)", key, Dates.now());
                }
            }
        }
        return rows.size();
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    public List<Map<String, Object>> trash() {
        return jdbc.queryForList("select id, entity, entity_id as entityId, label, summary, reason, deleted_by as deletedBy, deleted_at as deletedAt, length(data) as bytes from deleted_records order by deleted_at desc, id desc limit 300");
    }

    public String trashData(long id) {
        List<String> l = jdbc.queryForList("select data from deleted_records where id = ?", String.class, id);
        if (l.isEmpty()) throw ApiException.notFound("El registro eliminado");
        return l.get(0);
    }
}
