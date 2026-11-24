package pe.clinicacardenas.clinical;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Catálogo de procedimientos con variantes (familia) y promociones con vigencia.
 * Una promoción puede bajar el precio en %, en un monto o fijar un precio promocional; aplica a un procedimiento
 * o a toda una familia. Si hay varias vigentes se aplica la que deja el menor precio (no se acumulan).
 * El precio de lista se guarda junto al precio final en el plan de tratamiento, así el paciente ve el descuento
 * (Código de Protección al Consumidor, Ley 29571: información de precios veraz y condiciones visibles).
 */
@Service
public class PromotionService {
    private static final String PORCENTAJE = "PORCENTAJE";
    private static final String DISCOUNTTYPE = "discountType";
    private static final String LISTPRICE = "listPrice";
    private static final String PROCEDUREID = "procedureId";
    private static final String VALIDTO = "validTo";
    private static final String VALUE = "value";


    public static final List<String> TYPES = List.of(PORCENTAJE, "MONTO", "PRECIO");

    private final JdbcTemplate jdbc;
    private final ProcedureRepository procedures;
    private final AuditService audit;

    public PromotionService(JdbcTemplate jdbc, ProcedureRepository procedures, AuditService audit) {
        this.jdbc = jdbc;
        this.procedures = procedures;
        this.audit = audit;
    }

    public record PromoRequest(String name, Long procedureId, String family, String discountType, BigDecimal value,
                               LocalDate validFrom, LocalDate validTo, String conditions, Boolean active) {}

    public record ProcedureRequest(String code, String name, String category, String family, BigDecimal price,
                                   String description, Integer durationMin, Boolean active) {}

    public record Applied(BigDecimal price, BigDecimal listPrice, Long promotionId, String promotionName) {}

    /** Precio final de un procedimiento hoy, con la mejor promoción vigente. */
    public Applied priceFor(Procedure p, LocalDate day) {
        BigDecimal best = p.price;
        Map<String, Object> bestPromo = null;
        for (Map<String, Object> pr : active(day)) {
            Object pid = pr.get(PROCEDUREID);
            boolean applies = pid != null ? ((Number) pid).longValue() == p.id
                    : pr.get("family") != null && pr.get("family").equals(p.family);
            if (!applies) continue;
            BigDecimal v = apply(p.price, (String) pr.get(DISCOUNTTYPE), (BigDecimal) pr.get(VALUE));
            if (v.compareTo(best) < 0) { best = v; bestPromo = pr; }
        }
        if (bestPromo == null) return new Applied(p.price, null, null, null);
        return new Applied(best, p.price, ((Number) bestPromo.get("id")).longValue(), (String) bestPromo.get("name"));
    }

    static BigDecimal apply(BigDecimal price, String type, BigDecimal value) {
        BigDecimal r = switch (type) {
            case PORCENTAJE -> price.subtract(price.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            case "MONTO" -> price.subtract(value);
            default -> value;
        };
        return r.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> active(LocalDate day) {
        return jdbc.queryForList("""
                select id, name, procedure_id as procedureId, family, discount_type as discountType, value, valid_to as validTo, conditions
                  from promotions where active = true and ? between valid_from and valid_to""", day);
    }

    /** Catálogo para elegir en el plan: cada procedimiento con su promoción vigente, agrupado por familia en el front. */
    @Transactional(readOnly = true)
    public List<Procedure> catalog(boolean includeInactive) {
        List<Procedure> list = includeInactive ? procedures.findAll() : procedures.findByActiveTrueOrderByCategoryAscNameAsc();
        LocalDate today = Dates.today();
        for (Procedure p : list) {
            Applied a = priceFor(p, today);
            if (a.promotionId() != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.promotionId());
                m.put("name", a.promotionName());
                m.put("price", a.price());
                m.put(LISTPRICE, a.listPrice());
                p.promotion = m;
            }
        }
        list.sort(Comparator.comparing((Procedure p) -> p.category).thenComparing(p -> p.family == null ? p.name : p.family).thenComparing(p -> p.price));
        return list;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> promotions() {
        return promotionsInternal();
    }

    private List<Map<String, Object>> promotionsInternal() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select pr.id, pr.name, pr.procedure_id as procedureId, p.name as procedureName, pr.family, pr.discount_type as discountType,
                       pr.value, pr.valid_from as validFrom, pr.valid_to as validTo, pr.conditions, pr.active, p.price as listPrice,
                       (select count(*) from treatment_items ti where ti.promotion_id = pr.id) as uses
                  from promotions pr left join procedures p on p.id = pr.procedure_id
                 order by pr.active desc, pr.valid_to desc""");
        LocalDate today = Dates.today();
        for (Map<String, Object> r : rows) {
            LocalDate f = ((java.sql.Date) r.get("validFrom")).toLocalDate();
            LocalDate t = ((java.sql.Date) r.get(VALIDTO)).toLocalDate();
            boolean on = Boolean.TRUE.equals(r.get("active")) || Integer.valueOf(1).equals(r.get("active"));
            r.put("state", promotionState(on, today, f, t));
            if (r.get(LISTPRICE) != null) r.put("finalPrice", apply((BigDecimal) r.get(LISTPRICE), (String) r.get(DISCOUNTTYPE), (BigDecimal) r.get(VALUE)));
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> savePromotion(Long id, PromoRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 3) e.put("name", "Ponle un nombre a la promoción.");
        String type = r.discountType() == null ? "" : r.discountType().toUpperCase();
        if (!TYPES.contains(type)) e.put(DISCOUNTTYPE, "Selecciona el tipo de descuento.");
        if (r.value() == null || r.value().signum() <= 0) e.put(VALUE, "Ingresa el valor.");
        else if (PORCENTAJE.equals(type) && r.value().compareTo(new BigDecimal("90")) > 0) e.put(VALUE, "Máximo 90 %.");
        if (r.procedureId() == null && (r.family() == null || r.family().isBlank())) e.put(PROCEDUREID, "Elige el procedimiento o la familia a la que aplica.");
        if (r.validFrom() == null || r.validTo() == null) e.put(VALIDTO, "Indica desde y hasta cuándo es válida.");
        else if (r.validTo().isBefore(r.validFrom())) e.put(VALIDTO, "La fecha final debe ser posterior a la inicial.");
        if (r.procedureId() != null) {
            Procedure p = procedures.findById(r.procedureId()).orElse(null);
            if (p == null) e.put(PROCEDUREID, "El procedimiento no existe.");
            else if (r.value() != null && TYPES.contains(type) && !PORCENTAJE.equals(type) && r.value().compareTo(p.price) >= 0) {
                e.put(VALUE, "PRECIO".equals(type) ? "El precio promocional debe ser menor al de lista (" + p.price + ")." : "El descuento no puede ser mayor o igual al precio.");
            }
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa la promoción.", e);
        String fam = r.procedureId() != null ? null : r.family().trim();
        if (id == null) {
            jdbc.update("""
                    insert into promotionsInternal(name, procedure_id, family, discount_type, value, valid_from, valid_to, conditions, active, created_at, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", name, r.procedureId(), fam, type, r.value(), r.validFrom(), r.validTo(),
                    blank(r.conditions()), r.active() == null || r.active(), Dates.now(), CurrentUser.usernameOrSystem());
            audit.register("PROMOCION", null, "CREAR", name);
        } else {
            int n = jdbc.update("""
                    update promotions set name = ?, procedure_id = ?, family = ?, discount_type = ?, value = ?, valid_from = ?, valid_to = ?,
                           conditions = ?, active = ? where id = ?""", name, r.procedureId(), fam, type, r.value(), r.validFrom(), r.validTo(),
                    blank(r.conditions()), r.active() == null || r.active(), id);
            if (n == 0) throw ApiException.notFound("La promoción");
            audit.register("PROMOCION", id, "EDITAR", name);
        }
        return promotionsInternal();
    }

    @Transactional
    public Procedure saveProcedure(Long id, ProcedureRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        Procedure p = id == null ? new Procedure() : procedures.findById(id).orElseThrow(() -> ApiException.notFound("El procedimiento"));
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 3) e.put("name", "Escribe el nombre del procedimiento.");
        if (r.price() == null || r.price().signum() < 0) e.put("price", "Ingresa el precio de lista.");
        if (id == null) {
            String code = r.code() == null ? "" : r.code().trim().toUpperCase();
            if (!code.matches("[A-Z]{3}-\\d{3}")) e.put("code", "Código con el formato ABC-001.");
            else if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from procedures where code = ?", Boolean.class, code))) e.put("code", "Ese código ya existe.");
            p.code = code;
            if (r.category() == null || r.category().isBlank()) e.put("category", "Selecciona la categoría.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa el procedimiento.", e);
        p.name = name;
        if (r.category() != null && !r.category().isBlank()) p.category = r.category().trim();
        p.family = blank(r.family());
        p.price = r.price().setScale(2, RoundingMode.HALF_UP);
        p.description = blank(r.description());
        p.durationMin = r.durationMin();
        if (r.active() != null) p.active = r.active();
        procedures.save(p);
        audit.register("PROCEDIMIENTO", p.id, id == null ? "CREAR" : "EDITAR", p.code + " " + p.name + " · S/ " + p.price);
        return p;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String promotionState(boolean on, LocalDate today, LocalDate from, LocalDate to) {
        if (!on) return "INACTIVA";
        if (today.isBefore(from)) return "PROGRAMADA";
        return today.isAfter(to) ? "VENCIDA" : "VIGENTE";
    }
}
