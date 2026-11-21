package pe.clinicacardenas.commission;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.agenda.Dentist;
import pe.clinicacardenas.agenda.DentistRepository;
import pe.clinicacardenas.cash.CashService;
import pe.clinicacardenas.clinical.Procedure;
import pe.clinicacardenas.clinical.ProcedureRepository;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * HU-16 reglas de comisión · HU-17 cálculo por odontólogo y período · HU-18 liquidación.
 *
 * Un procedimiento genera comisión para el odontólogo que lo realizó, con la regla vigente en la fecha en que
 * se realizó (la regla de ese procedimiento tiene prioridad sobre la regla general). Con base COBRADO solo se
 * comisiona cuando el procedimiento está pagado por completo. Cada procedimiento se liquida una sola vez.
 */
@Service
public class CommissionService {

    private final CommissionRuleRepository rules;
    private final CommissionSettlementRepository settlements;
    private final CommissionLineRepository lines;
    private final CommissionPayoutRepository payouts;
    private final DentistRepository dentists;
    private final ProcedureRepository procedures;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CommissionService(CommissionRuleRepository rules, CommissionSettlementRepository settlements,
                             CommissionLineRepository lines, CommissionPayoutRepository payouts,
                             DentistRepository dentists, ProcedureRepository procedures, JdbcTemplate jdbc, AuditService audit) {
        this.rules = rules;
        this.settlements = settlements;
        this.lines = lines;
        this.payouts = payouts;
        this.dentists = dentists;
        this.procedures = procedures;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record RuleRequest(Long dentistId, Long procedureId, String calcType, BigDecimal value, String base,
                              LocalDate validFrom, LocalDate validTo, Boolean active, String notes) {}

    public record SettleRequest(Long dentistId, LocalDate from, LocalDate to, List<Long> itemIds, String notes) {}

    public record PayoutRequest(BigDecimal amount, LocalDate paidOn, String method, String reference) {}

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private Dentist dentist(Long id) {
        if (id == null) throw ApiException.field("dentistId", "Selecciona al odontólogo.");
        return dentists.findById(id).orElseThrow(() -> ApiException.notFound("El odontólogo"));
    }

    // ------------------------------------------------------------------ HU-16 reglas

    public List<CommissionRule> listRules(Long dentistId) {
        return dentistId == null ? rules.findAllByOrderByDentistIdAscValidFromDesc() : rules.findByDentistIdOrderByValidFromDesc(dentistId);
    }

    private boolean ruleUsed(Long ruleId) {
        Long n = jdbc.queryForObject("select count(*) from commission_lines where rule_id = ?", Long.class, ruleId);
        return n != null && n > 0;
    }

    @Transactional
    public CommissionRule saveRule(Long id, RuleRequest r) {
        CommissionRule rule = id == null ? new CommissionRule() : rules.findById(id).orElseThrow(() -> ApiException.notFound("La regla"));
        boolean used = id != null && ruleUsed(id);
        Map<String, String> e = new LinkedHashMap<>();

        if (!used) {
            Dentist d = r.dentistId() == null ? null : dentists.findById(r.dentistId()).orElse(null);
            if (d == null) e.put("dentistId", "Selecciona al odontólogo.");
            Procedure p = null;
            if (r.procedureId() != null) {
                p = procedures.findById(r.procedureId()).orElse(null);
                if (p == null) e.put("procedureId", "El procedimiento no existe.");
            }
            String type = r.calcType() == null ? "" : r.calcType().trim().toUpperCase();
            if (!List.of("PORCENTAJE", "MONTO_FIJO").contains(type)) e.put("calcType", "Elige porcentaje o monto fijo.");
            BigDecimal v = r.value() == null ? null : money(r.value());
            if (v == null || v.signum() <= 0) e.put("value", "Ingresa un valor mayor a cero.");
            else if ("PORCENTAJE".equals(type) && v.compareTo(new BigDecimal("100")) > 0) e.put("value", "El porcentaje no puede superar 100 %.");
            else if ("MONTO_FIJO".equals(type) && p != null && v.compareTo(p.price) > 0) {
                e.put("value", "El monto fijo no puede superar el precio del procedimiento (S/ " + p.price.toPlainString() + ").");
            }
            String base = r.base() == null ? "REALIZADO" : r.base().trim().toUpperCase();
            if (!List.of("REALIZADO", "COBRADO").contains(base)) e.put("base", "Elige la base de cálculo.");
            if (r.validFrom() == null) e.put("validFrom", "Indica desde cuándo rige la regla.");
            rule.dentist = d;
            rule.procedure = p;
            rule.calcType = type;
            rule.value = v;
            rule.base = base;
            rule.validFrom = r.validFrom();
        } else if (r.validFrom() != null && !r.validFrom().equals(rule.validFrom)) {
            e.put("validFrom", "La regla ya se usó en una liquidación: solo puedes cambiar su fin de vigencia o desactivarla.");
        }
        if (r.validTo() != null && rule.validFrom != null && r.validTo().isBefore(rule.validFrom)) {
            e.put("validTo", "El fin de vigencia no puede ser anterior al inicio.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos de la regla.", e);

        rule.validTo = r.validTo();
        rule.active = r.active() == null || r.active();
        rule.notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();

        if (rule.active) {
            for (CommissionRule o : rules.findByDentistIdOrderByValidFromDesc(rule.dentist.id)) {
                if (!o.active || o.id.equals(rule.id)) continue;
                boolean sameScope = Objects.equals(o.procedure == null ? null : o.procedure.id, rule.procedure == null ? null : rule.procedure.id);
                LocalDate oEnd = o.validTo == null ? LocalDate.MAX : o.validTo;
                LocalDate rEnd = rule.validTo == null ? LocalDate.MAX : rule.validTo;
                if (sameScope && !o.validFrom.isAfter(rEnd) && !rule.validFrom.isAfter(oEnd)) {
                    throw ApiException.conflict("REGLA_SUPERPUESTA", "Ya existe una regla vigente para "
                            + (rule.procedure == null ? "todos los procedimientos" : rule.procedure.name) + " de "
                            + rule.dentist.getFullName() + " en esas fechas (" + o.getLabel() + ", desde " + o.validFrom
                            + "). Cierra su vigencia antes de crear otra.",
                            Map.of("validFrom", "Se cruza con otra regla vigente."));
                }
            }
        }
        LocalDateTime now = Dates.now();
        if (rule.id == null) {
            rule.createdAt = now;
            rule.createdBy = CurrentUser.usernameOrSystem();
        } else {
            rule.updatedAt = now;
            rule.updatedBy = CurrentUser.usernameOrSystem();
        }
        rules.save(rule);
        audit.record("COMISION_REGLA", rule.id, id == null ? "CREAR" : "EDITAR", rule.dentist.getFullName() + " · " + rule.getLabel()
                + " · desde " + rule.validFrom + (rule.validTo == null ? "" : " hasta " + rule.validTo) + (rule.active ? "" : " · inactiva"));
        return rule;
    }

    // ------------------------------------------------------------------ HU-17 cálculo

    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("PERIODO", "Indica el período.");
        if (to.isBefore(from)) throw ApiException.field("to", "La fecha final no puede ser anterior a la inicial.");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.field("to", "El período no puede superar un año.");
    }

    private Optional<CommissionRule> ruleFor(List<CommissionRule> rs, long procedureId, LocalDate day) {
        CommissionRule general = null;
        for (CommissionRule r : rs) {
            if (!r.appliesOn(day)) continue;
            if (r.procedure != null && r.procedure.id == procedureId) return Optional.of(r);
            if (r.procedure == null && general == null) general = r;
        }
        return Optional.ofNullable(general);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calculate(Long dentistId, LocalDate from, LocalDate to) {
        Dentist d = dentist(dentistId);
        checkPeriod(from, to);
        List<CommissionRule> rs = rules.findByDentistIdOrderByValidFromDesc(d.id);
        List<Map<String, Object>> eligible = new ArrayList<>(), noRule = new ArrayList<>(), unpaid = new ArrayList<>(), settled = new ArrayList<>();
        BigDecimal baseTotal = BigDecimal.ZERO, total = BigDecimal.ZERO, settledTotal = BigDecimal.ZERO;

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select ti.id, ti.price, ti.done_at, ti.procedure_id, ti.tooth, pr.name as procedure_name,
                       concat(p.first_name, ' ', p.last_name) as patient_name, p.id as patient_id,
                       coalesce((select sum(py.amount) from payments py where py.treatment_item_id = ti.id and py.status = 'VALIDO'), 0) as paid,
                       (select cl.settlement_id from commission_lines cl where cl.treatment_item_id = ti.id) as settlement_id,
                       (select cl.commission_amount from commission_lines cl where cl.treatment_item_id = ti.id) as settled_amount
                  from treatment_items ti
                  join treatment_plans tp on tp.id = ti.plan_id
                  join patients p on p.id = tp.patient_id
                  join procedures pr on pr.id = ti.procedure_id
                 where ti.status = 'REALIZADO' and ti.done_at >= ? and ti.done_at < ?
                   and coalesce(ti.done_by_dentist_id, tp.dentist_id) = ?
                 order by ti.done_at
                """, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), d.id);

        for (Map<String, Object> row : rows) {
            LocalDateTime doneAt = Dates.toLocalDateTime(row.get("done_at"));
            BigDecimal price = money((BigDecimal) row.get("price"));
            BigDecimal paid = money((BigDecimal) row.get("paid"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("treatmentItemId", ((Number) row.get("id")).longValue());
            m.put("doneAt", doneAt);
            m.put("patientId", ((Number) row.get("patient_id")).longValue());
            m.put("patientName", row.get("patient_name"));
            m.put("procedure", row.get("procedure_name") + (row.get("tooth") == null ? "" : " · pieza " + row.get("tooth")));
            m.put("price", price);
            m.put("paid", paid);
            if (row.get("settlement_id") != null) {
                m.put("settlementId", ((Number) row.get("settlement_id")).longValue());
                m.put("commission", money((BigDecimal) row.get("settled_amount")));
                settledTotal = settledTotal.add((BigDecimal) m.get("commission"));
                settled.add(m);
                continue;
            }
            Optional<CommissionRule> rule = ruleFor(rs, ((Number) row.get("procedure_id")).longValue(), doneAt.toLocalDate());
            if (rule.isEmpty()) {
                noRule.add(m);
                continue;
            }
            CommissionRule r = rule.get();
            m.put("ruleId", r.id);
            m.put("rule", r.getLabel());
            if ("COBRADO".equals(r.base) && paid.compareTo(price) < 0) {
                m.put("reason", paid.signum() == 0 ? "Sin cobro registrado" : "Cobrado S/ " + paid.toPlainString() + " de S/ " + price.toPlainString());
                unpaid.add(m);
                continue;
            }
            BigDecimal commission = "PORCENTAJE".equals(r.calcType)
                    ? money(price.multiply(r.value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))
                    : money(r.value);
            m.put("base", price);
            m.put("commission", commission);
            baseTotal = baseTotal.add(price);
            total = total.add(commission);
            eligible.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dentist", Map.of("id", d.id, "fullName", d.getFullName(), "specialty", d.specialty));
        out.put("from", from);
        out.put("to", to);
        out.put("lines", eligible);
        out.put("baseTotal", money(baseTotal));
        out.put("total", money(total));
        out.put("withoutRule", noRule);
        out.put("pendingCollection", unpaid);
        out.put("alreadySettled", settled);
        out.put("alreadySettledTotal", money(settledTotal));
        out.put("rules", rs.stream().filter(r -> r.active).map(r -> Map.of("id", r.id, "label", r.getLabel(),
                "validFrom", r.validFrom, "validTo", r.validTo == null ? "" : r.validTo.toString())).toList());
        return out;
    }

    // ------------------------------------------------------------------ HU-18 liquidación

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> settle(SettleRequest r) {
        Dentist d = dentists.lockById(dentist(r.dentistId()).id).orElseThrow();
        Map<String, Object> calc = calculate(d.id, r.from(), r.to());
        List<Map<String, Object>> eligible = (List<Map<String, Object>>) calc.get("lines");
        Set<Long> chosen = r.itemIds() == null || r.itemIds().isEmpty() ? null : new HashSet<>(r.itemIds());
        List<Map<String, Object>> selected = eligible.stream()
                .filter(m -> chosen == null || chosen.contains((Long) m.get("treatmentItemId"))).toList();
        if (selected.isEmpty()) {
            throw ApiException.badRequest("SIN_COMISIONES", "No hay comisiones por liquidar con la selección y el período indicados.");
        }
        if (chosen != null && selected.size() != chosen.size()) {
            throw ApiException.conflict("SELECCION_INVALIDA", "Algunos procedimientos seleccionados ya no son liquidables (ya liquidados, sin regla o sin cobro). Vuelve a calcular.");
        }
        BigDecimal total = selected.stream().map(m -> (BigDecimal) m.get("commission")).reduce(BigDecimal.ZERO, BigDecimal::add);

        CommissionSettlement s = new CommissionSettlement();
        s.dentist = d;
        s.periodFrom = r.from();
        s.periodTo = r.to();
        s.totalAmount = money(total);
        s.paidAmount = BigDecimal.ZERO;
        s.status = "PENDIENTE";
        s.notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        s.createdAt = Dates.now();
        s.createdBy = CurrentUser.usernameOrSystem();
        settlements.save(s);
        try {
            for (Map<String, Object> m : selected) {
                CommissionLine l = new CommissionLine();
                l.settlementId = s.id;
                l.treatmentItemId = (Long) m.get("treatmentItemId");
                l.ruleId = (Long) m.get("ruleId");
                l.doneAt = (LocalDateTime) m.get("doneAt");
                l.patientName = (String) m.get("patientName");
                l.procedureName = (String) m.get("procedure");
                l.ruleLabel = (String) m.get("rule");
                l.baseAmount = (BigDecimal) m.get("base");
                l.commissionAmount = (BigDecimal) m.get("commission");
                lines.save(l);
            }
            lines.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("YA_LIQUIDADO", "Otro usuario acaba de liquidar alguno de estos procedimientos. Vuelve a calcular.");
        }
        audit.record("LIQUIDACION", s.id, "CREAR", d.getFullName() + " · " + r.from() + " a " + r.to() + " · "
                + selected.size() + " procedimientos · S/ " + s.totalAmount.toPlainString());
        return detail(s.id);
    }

    @Transactional
    public Map<String, Object> pay(Long id, PayoutRequest r) {
        CommissionSettlement s = settlements.lockById(id).orElseThrow(() -> ApiException.notFound("La liquidación"));
        if ("ANULADA".equals(s.status)) throw ApiException.conflict("LIQUIDACION_ANULADA", "La liquidación está anulada.");
        if ("PAGADA".equals(s.status)) throw ApiException.conflict("LIQUIDACION_PAGADA", "La liquidación ya está pagada.");
        Map<String, String> e = new LinkedHashMap<>();
        BigDecimal pending = s.getPendingAmount();
        BigDecimal amount = r.amount() == null ? null : money(r.amount());
        if (amount == null || amount.signum() <= 0) e.put("amount", "Ingresa el importe pagado.");
        else if (amount.compareTo(pending) > 0) e.put("amount", "El importe supera lo pendiente (S/ " + pending.toPlainString() + ").");
        LocalDate on = r.paidOn() == null ? Dates.today() : r.paidOn();
        if (on.isAfter(Dates.today())) e.put("paidOn", "La fecha de pago no puede ser futura.");
        if (on.isBefore(s.createdAt.toLocalDate())) e.put("paidOn", "La fecha de pago no puede ser anterior a la liquidación.");
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (method == null || !CashService.METHODS.contains(method)) e.put("method", "Selecciona el medio de pago.");
        if (method != null && !"EFECTIVO".equals(method) && (r.reference() == null || r.reference().isBlank())) {
            e.put("reference", "Ingresa el número de operación.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del pago.", e);
        CommissionPayout p = new CommissionPayout();
        p.settlementId = s.id;
        p.paidOn = on;
        p.amount = amount;
        p.method = method;
        p.reference = r.reference() == null || r.reference().isBlank() ? null : r.reference().trim();
        p.createdAt = Dates.now();
        p.createdBy = CurrentUser.usernameOrSystem();
        payouts.save(p);
        s.paidAmount = money(s.paidAmount.add(amount));
        s.status = s.paidAmount.compareTo(s.totalAmount) >= 0 ? "PAGADA" : "PARCIAL";
        settlements.save(s);
        audit.record("LIQUIDACION", s.id, "PAGO", s.dentist.getFullName() + " · S/ " + amount.toPlainString() + " · "
                + CashService.METHOD_LABEL.get(method) + " · estado " + s.status.toLowerCase());
        return detail(s.id);
    }

    @Transactional
    public Map<String, Object> annul(Long id, String reason) {
        CommissionSettlement s = settlements.lockById(id).orElseThrow(() -> ApiException.notFound("La liquidación"));
        if ("ANULADA".equals(s.status)) throw ApiException.conflict("LIQUIDACION_ANULADA", "La liquidación ya está anulada.");
        if (s.paidAmount.signum() > 0) {
            throw ApiException.conflict("LIQUIDACION_CON_PAGOS", "No se puede anular una liquidación con pagos registrados.");
        }
        if (reason == null || reason.trim().length() < 5) throw ApiException.field("reason", "Explica el motivo de la anulación.");
        lines.deleteBySettlementId(s.id);   // libera los procedimientos para una nueva liquidación
        s.status = "ANULADA";
        s.notes = (s.notes == null ? "" : s.notes + " · ") + "Anulada: " + reason.trim();
        settlements.save(s);
        audit.record("LIQUIDACION", s.id, "ANULAR", s.dentist.getFullName() + " · " + reason.trim());
        return detail(s.id);
    }

    public List<CommissionSettlement> listSettlements(Long dentistId, String status) {
        return settlements.findAllByOrderByCreatedAtDesc().stream()
                .filter(s -> dentistId == null || s.dentist.id.equals(dentistId))
                .filter(s -> status == null || status.isBlank() || s.status.equals(status))
                .toList();
    }

    public Map<String, Object> detail(Long id) {
        CommissionSettlement s = settlements.findById(id).orElseThrow(() -> ApiException.notFound("La liquidación"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settlement", s);
        m.put("lines", lines.findBySettlementIdOrderByDoneAtAsc(id));
        m.put("payouts", payouts.findBySettlementIdOrderByPaidOnAsc(id));
        return m;
    }

    /** Resumen por odontólogo: lo pendiente de pagar en liquidaciones. */
    public List<Map<String, Object>> summary() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Dentist d : dentists.findAvailable()) {
            BigDecimal pending = BigDecimal.ZERO, paid = BigDecimal.ZERO;
            for (CommissionSettlement s : listSettlements(d.id, null)) {
                if ("ANULADA".equals(s.status)) continue;
                pending = pending.add(s.getPendingAmount());
                paid = paid.add(s.paidAmount);
            }
            long activeRules = rules.findByDentistIdOrderByValidFromDesc(d.id).stream().filter(r -> r.appliesOn(Dates.today())).count();
            out.add(Map.of("dentistId", d.id, "fullName", d.getFullName(), "specialty", d.specialty, "color", d.color,
                    "pending", money(pending), "paid", money(paid), "activeRules", activeRules));
        }
        return out;
    }
}
