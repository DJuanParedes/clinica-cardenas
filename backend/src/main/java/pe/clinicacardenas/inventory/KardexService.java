package pe.clinicacardenas.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.Dates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * v2.8: kardex valorizado por material (entradas, salidas y saldo en cantidad y en soles) y consumo de materiales
 * por paciente, por servicio (procedimiento) y por material, con el margen de cada servicio.
 * Las salidas se valorizan al costo de los lotes que consumen (primero el que vence antes).
 */
@Service
public class KardexService {
    private static final String UNIT_COST = "unit_cost";
    private static final String VALUE = "value";
    private static final String VALUE_AFTER = "value_after";


    private final JdbcTemplate jdbc;

    public KardexService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final Map<String, String> KIND = Map.of("ENTRADA", "Entrada", "SALIDA", "Salida", "AJUSTE", "Ajuste de inventario", "CONSUMO", "Consumo en atención");

    @Transactional(readOnly = true)
    public Map<String, Object> kardex(Long itemId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> it = jdbc.queryForList("select id, name, unit, category, stock, unit_cost from inventory_items where id = ?", itemId);
        if (it.isEmpty()) throw ApiException.notFound("El material");
        LocalDate f = from == null ? Dates.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? Dates.today() : to;
        if (t.isBefore(f)) throw ApiException.field("to", "La fecha final no puede ser anterior a la inicial.");
        // saldo inicial: el último movimiento antes del período
        List<Map<String, Object>> open = jdbc.queryForList(
                "select stock_after, value_after from inventory_moves where item_id = ? and created_at < ? order by id desc limit 1", itemId, f.atStartOfDay());
        BigDecimal qty0;
        BigDecimal val0;
        if (!open.isEmpty()) {
            qty0 = (BigDecimal) open.get(0).get("stock_after");
            val0 = open.get(0).get(VALUE_AFTER) == null ? BigDecimal.ZERO : (BigDecimal) open.get(0).get(VALUE_AFTER);
        } else {
            // sin movimientos antes: el saldo inicial es lo que había antes del primer movimiento (stock cargado al crear el material)
            List<Map<String, Object>> first = jdbc.queryForList(
                    "select stock_after - quantity as q, coalesce(value_after, 0) - coalesce(total_cost, 0) as v from inventory_moves where item_id = ? order by id limit 1", itemId);
            qty0 = first.isEmpty() ? (BigDecimal) it.get(0).get("stock") : ((BigDecimal) first.get(0).get("q"));
            val0 = first.isEmpty() ? qty0.multiply(it.get(0).get(UNIT_COST) == null ? BigDecimal.ZERO : (BigDecimal) it.get(0).get(UNIT_COST))
                    : ((BigDecimal) first.get(0).get("v")).max(BigDecimal.ZERO);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal inQ = BigDecimal.ZERO;
        BigDecimal inV = BigDecimal.ZERO;
        BigDecimal outQ = BigDecimal.ZERO;
        BigDecimal outV = BigDecimal.ZERO;
        for (Map<String, Object> m : jdbc.queryForList("""
                select m.id, m.created_at, m.kind, m.quantity, m.unit_cost, m.total_cost, m.stock_after, m.value_after, m.reason, m.created_by,
                       m.patient_id, concat(p.first_name, ' ', p.last_name) as patient, pr.name as procedure_name,
                       pu.doc_number as purchase_doc, pu.supplier, l.lot_code
                  from inventory_moves m
                  left join patients p on p.id = m.patient_id
                  left join procedures pr on pr.id = m.procedure_id
                  left join inventory_purchases pu on pu.id = m.purchase_id
                  left join inventory_lots l on l.id = m.lot_id
                 where m.item_id = ? and m.created_at >= ? and m.created_at < ?
                 order by m.id
                """, itemId, f.atStartOfDay(), t.plusDays(1).atStartOfDay())) {
            BigDecimal q = (BigDecimal) m.get("quantity");
            BigDecimal tc = m.get("total_cost") == null ? BigDecimal.ZERO : (BigDecimal) m.get("total_cost");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.get("id"));
            r.put("date", m.get("created_at"));
            r.put("kind", m.get("kind"));
            r.put("kindLabel", KIND.getOrDefault((String) m.get("kind"), (String) m.get("kind")));
            String detail = m.get("reason") == null ? "" : (String) m.get("reason");
            if (m.get("supplier") != null) detail = (detail.isEmpty() ? "" : detail + " · ") + m.get("supplier") + (m.get("purchase_doc") == null ? "" : " " + m.get("purchase_doc"));
            if (m.get("lot_code") != null) detail += (detail.isEmpty() ? "" : " · ") + "lote " + m.get("lot_code");
            r.put("detail", detail);
            r.put("patient", m.get("patient_id") == null ? null : Map.of("id", m.get("patient_id"), "fullName", m.get("patient")));
            r.put("procedure", m.get("procedure_name"));
            r.put("unitCost", m.get(UNIT_COST));
            if (q.signum() >= 0) { r.put("inQty", q); r.put("inValue", tc); inQ = inQ.add(q); inV = inV.add(tc); }
            else { r.put("outQty", q.negate()); r.put("outValue", tc.negate()); outQ = outQ.add(q.negate()); outV = outV.add(tc.negate()); }
            BigDecimal sq = (BigDecimal) m.get("stock_after");
            BigDecimal sv = m.get(VALUE_AFTER) == null ? BigDecimal.ZERO : (BigDecimal) m.get(VALUE_AFTER);
            r.put("balanceQty", sq);
            r.put("balanceValue", sv);
            r.put("averageCost", sq.signum() > 0 ? sv.divide(sq, 4, RoundingMode.HALF_UP) : null);
            r.put("by", m.get("created_by"));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", it.get(0));
        out.put("from", f);
        out.put("to", t);
        out.put("opening", Map.of("qty", qty0, VALUE, val0));
        out.put("rows", rows);
        BigDecimal closeQ = rows.isEmpty() ? qty0 : (BigDecimal) rows.get(rows.size() - 1).get("balanceQty");
        BigDecimal closeV = rows.isEmpty() ? val0 : (BigDecimal) rows.get(rows.size() - 1).get("balanceValue");
        out.put("totals", Map.of("inQty", inQ, "inValue", inV, "outQty", outQ, "outValue", outV));
        out.put("closing", Map.of("qty", closeQ, VALUE, closeV));
        return out;
    }

    /** Valor total del inventario al día de hoy, por categoría. */
    @Transactional(readOnly = true)
    public Map<String, Object> valuation() {
        List<Map<String, Object>> items = jdbc.queryForList("""
                select i.id, i.name, i.category, i.unit, i.stock,
                       coalesce((select m.value_after from inventory_moves m where m.item_id = i.id order by m.id desc limit 1), round(greatest(i.stock, 0) * coalesce(i.unit_cost, 0), 2)) as value
                  from inventory_items i where i.active = true order by value desc, i.name
                """);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCat = new LinkedHashMap<>();
        for (Map<String, Object> r : items) {
            BigDecimal v = (BigDecimal) r.get(VALUE);
            total = total.add(v);
            byCat.merge((String) r.get("category"), v, BigDecimal::add);
        }
        return Map.of("total", total, "byCategory", byCat, "items", items);
    }

    /** Consumo de materiales en un período: por servicio (con su margen), por paciente y por material. */
    @Transactional(readOnly = true)
    public Map<String, Object> consumption(LocalDate from, LocalDate to, Long patientId) {
        LocalDate f = from == null ? Dates.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? Dates.today() : to;
        if (t.isBefore(f)) throw ApiException.field("to", "La fecha final no puede ser anterior a la inicial.");
        Object[] a = patientId == null ? new Object[]{f.atStartOfDay(), t.plusDays(1).atStartOfDay()}
                : new Object[]{f.atStartOfDay(), t.plusDays(1).atStartOfDay(), patientId};
        String where = " m.created_at >= ? and m.created_at < ? and m.quantity < 0 and m.kind in ('CONSUMO', 'SALIDA') and m.patient_id is not null"
                + (patientId == null ? "" : " and m.patient_id = ?");
        List<Map<String, Object>> byProcedure = jdbc.queryForList("""
                select pr.id, pr.name, count(distinct m.treatment_item_id) as times, -sum(m.total_cost) as materials,
                       (select coalesce(sum(ti.price), 0) from treatment_items ti where ti.id in
                          (select distinct m2.treatment_item_id from inventory_moves m2 where m2.procedure_id = pr.id and m2.created_at >= ? and m2.created_at < ?
                           and m2.treatment_item_id is not null %s)) as revenue
                  from inventory_moves m join procedures pr on pr.id = m.procedure_id
                 where %s
                 group by pr.id, pr.name order by materials desc
                """.formatted(patientId == null ? "" : "and m2.patient_id = " + patientId.longValue(), where),
                concat(new Object[]{f.atStartOfDay(), t.plusDays(1).atStartOfDay()}, a));
        for (Map<String, Object> r : byProcedure) {
            BigDecimal mat = (BigDecimal) r.get("materials");
            BigDecimal rev = (BigDecimal) r.get("revenue");
            long times = ((Number) r.get("times")).longValue();
            r.put("perService", times == 0 ? BigDecimal.ZERO : mat.divide(BigDecimal.valueOf(times), 2, RoundingMode.HALF_UP));
            r.put("materialShare", rev.signum() > 0 ? mat.multiply(BigDecimal.valueOf(100)).divide(rev, 1, RoundingMode.HALF_UP) : null);
        }
        List<Map<String, Object>> byPatient = jdbc.queryForList("""
                select p.id, concat(p.first_name, ' ', p.last_name) as fullName, p.hc_number as hcNumber,
                       count(distinct m.treatment_item_id) as services, -sum(m.total_cost) as materials
                  from inventory_moves m join patients p on p.id = m.patient_id
                 where %s
                 group by p.id, p.first_name, p.last_name, p.hc_number order by materials desc limit 200
                """.formatted(where), a);
        List<Map<String, Object>> byItem = jdbc.queryForList("""
                select i.id, i.name, i.unit, -sum(m.quantity) as qty, -sum(m.total_cost) as materials
                  from inventory_moves m join inventory_items i on i.id = m.item_id
                 where %s
                 group by i.id, i.name, i.unit order by materials desc
                """.formatted(where), a);
        List<Map<String, Object>> detail = patientId == null ? List.of() : jdbc.queryForList("""
                select m.created_at as date, i.name as item, i.unit, -m.quantity as qty, -m.total_cost as cost, pr.name as procedureName, m.kind, m.reason
                  from inventory_moves m join inventory_items i on i.id = m.item_id left join procedures pr on pr.id = m.procedure_id
                 where %s order by m.created_at desc
                """.formatted(where), a);
        BigDecimal total = byItem.stream().map(r -> (BigDecimal) r.get("materials")).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", f);
        out.put("to", t);
        out.put("total", total);
        out.put("byProcedure", byProcedure);
        out.put("byPatient", byPatient);
        out.put("byItem", byItem);
        out.put("detail", detail);
        return out;
    }

    private static Object[] concat(Object[] x, Object[] y) {
        Object[] r = Arrays.copyOf(x, x.length + y.length);
        System.arraycopy(y, 0, r, x.length, y.length);
        return r;
    }
}
