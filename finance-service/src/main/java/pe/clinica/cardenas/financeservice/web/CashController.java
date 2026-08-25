package pe.clinica.cardenas.financeservice.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.financeservice.domain.CashSession;
import pe.clinica.cardenas.financeservice.domain.Expense;
import pe.clinica.cardenas.financeservice.domain.Payment;
import pe.clinica.cardenas.financeservice.repository.CashSessionRepository;
import pe.clinica.cardenas.financeservice.repository.ExpenseRepository;
import pe.clinica.cardenas.financeservice.repository.PaymentRepository;
import pe.clinica.cardenas.financeservice.service.EncounterClient;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.Locale;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class CashController {
    private final PaymentRepository payments;
    private final CashSessionRepository cashSessions;
    private final ExpenseRepository expenses;
    private final EncounterClient encounterClient;

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public List<Payment> payments() { return payments.findTop300ByOrderByPaidAtDesc(); }

    @GetMapping("/proformas/{proformaId}/payments-summary")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Map<String,Object> proformaPaymentSummary(@PathVariable Long proformaId) {
        List<Payment> list = payments.findAllByProformaIdOrderByPaidAtDesc(proformaId);
        BigDecimal total = list.stream().filter(p -> !"ANULADO".equals(p.getPaymentStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("proformaId", proformaId, "totalPaid", total, "paymentCount", list.size(), "payments", list);
    }

    @GetMapping("/patients/{patientId}/visit-payments")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Map<String,Object> visitPayments(@PathVariable Long patientId) {
        List<Payment> list = payments.findAllByPatientIdOrderByPaidAtDesc(patientId);
        BigDecimal total = list.stream().filter(p -> !"ANULADO".equals(p.getPaymentStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("patientId", patientId, "totalPaid", total, "payments", list);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String, Object> summary() {
        List<Payment> paymentList = payments.findAll();
        List<Expense> expenseList = expenses.findAll();
        BigDecimal paymentTotal = paymentList.stream().filter(p -> !"ANULADO".equals(p.getPaymentStatus()))
                .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Solo los gastos aprobados impactan el resumen financiero. REGISTRADO se conserva como
        // compatibilidad con gastos creados en versiones anteriores; los nuevos de Recepción quedan pendientes.
        BigDecimal expenseTotal = expenseList.stream().filter(e -> Set.of("APROBADO", "REGISTRADO").contains(e.getExpenseStatus()))
                .map(Expense::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> months = new ArrayList<>();
        List<BigDecimal> revenue = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            months.add(month.getMonth().getDisplayName(TextStyle.SHORT, new Locale("es", "PE")));
            BigDecimal value = paymentList.stream()
                    .filter(p -> p.getPaidAt() != null && YearMonth.from(p.getPaidAt()).equals(month) && !"ANULADO".equals(p.getPaymentStatus()))
                    .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            revenue.add(value);
        }
        long openCash = cashSessions.findAll().stream().filter(c -> "ABIERTA".equals(c.getCashStatus())).count();
        return Map.of("paymentTotal", paymentTotal, "expenseTotal", expenseTotal, "netCash", paymentTotal.subtract(expenseTotal),
                "paymentCount", paymentList.size(), "openCash", openCash, "months", months, "monthlyRevenue", revenue);
    }

    @GetMapping("/patients/{patientId}/pending-treatments")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public List<Map<String,Object>> pendingTreatments(@PathVariable Long patientId, HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        List<Map<String,Object>> result = new ArrayList<>();
        List<Map<String,Object>> contexts = safePaymentContext(patientId, auth);
        for (Map<String,Object> p : encounterClient.proformas(auth)) {
            if (!Objects.equals(longValue(p.get("patientId")), patientId)) continue;
            String status = text(p.get("status")).toUpperCase(Locale.ROOT);
            if (!Set.of("ACEPTADA","EN_TRATAMIENTO","COMPLETADA").contains(status)) continue;
            Long proformaId = longValue(p.get("id"));
            BigDecimal total = decimal(p.get("totalAmount"));
            BigDecimal paid = payments.findAllByProformaIdOrderByPaidAtDesc(proformaId).stream().filter(x -> !"ANULADO".equals(x.getPaymentStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal balance = total.subtract(paid).max(BigDecimal.ZERO);
            if (balance.signum() <= 0) continue;
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("payableKey", "PROFORMA-"+proformaId); row.put("source","PROFORMA"); row.put("proformaId",proformaId); row.put("proformaCode",p.get("code")); row.put("clinicalStatus",status);
            row.put("encounterId",null); row.put("appointmentId",p.get("appointmentId")); row.put("patientId",patientId); row.put("patientName",p.get("patientName")); row.put("doctorName",p.get("doctorName"));
            List<Map<String,Object>> lines = mapList(p.get("lines"));
            List<Payment> proformaPayments = payments.findAllByProformaIdOrderByPaidAtDesc(proformaId);
            List<Map<String,Object>> payableLines = new ArrayList<>();
            for (Map<String,Object> line : lines) {
                if ("CANCELADO".equalsIgnoreCase(text(line.get("treatmentStatus")))) continue;
                Map<String,Object> enriched = new LinkedHashMap<>(line);
                String ref = text(line.get("lineReference"));
                BigDecimal lineTotal = line.get("acceptedAmount") == null ? decimal(line.get("subtotal")) : decimal(line.get("acceptedAmount"));
                BigDecimal linePaid = proformaPayments.stream().filter(x -> !"ANULADO".equals(x.getPaymentStatus()) && ref.equals(x.getProformaLineReference()))
                        .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                enriched.put("paidAmount", linePaid);
                enriched.put("balance", lineTotal.subtract(linePaid).max(BigDecimal.ZERO));
                payableLines.add(enriched);
            }
            row.put("lines",payableLines);
            row.put("treatmentName",payableLines.stream().map(x->text(x.get("treatmentName"))).filter(x->!x.isBlank()).collect(java.util.stream.Collectors.joining(" + ")));
            row.put("date",p.get("acceptedAt")!=null?p.get("acceptedAt"):p.get("createdAt")); row.put("totalCost",total); row.put("paidAmount",paid); row.put("balance",balance); row.put("paymentStatus",paid.signum()==0?"SIN_PAGO":"PARCIAL");
            row.put("visits",contexts.stream().filter(c->Objects.equals(longValue(c.get("proformaId")),proformaId)&&!text(c.get("proformaLineReference")).isBlank()).toList());
            result.add(row);
        }
        // Compatibilidad con tratamientos antiguos no vinculados a proforma.
        for (Map<String,Object> legacy : encounterClient.pendingTreatments(patientId, auth)) {
            if (legacy.get("proformaId") != null) continue;
            Map<String,Object> row = new LinkedHashMap<>(legacy); row.put("payableKey","ENCOUNTER-"+legacy.get("encounterId")); row.put("source","ENCOUNTER"); row.put("lines",List.of()); row.put("visits",List.of()); result.add(row);
        }
        return result;
    }

    @GetMapping("/analysis")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String,Object> analysis() {
        List<Payment> validPayments = payments.findAll().stream().filter(p -> !"ANULADO".equals(p.getPaymentStatus())).toList();
        Map<String,Map<String,Object>> byMethod = new LinkedHashMap<>();
        for (Payment p : validPayments) {
            String method = p.getPaymentMethod() == null ? "OTRO" : p.getPaymentMethod();
            Map<String,Object> row = byMethod.computeIfAbsent(method, k -> { Map<String,Object> m = new LinkedHashMap<>(); m.put("name", k); m.put("total", BigDecimal.ZERO); m.put("count", 0L); return m; });
            row.put("total", ((BigDecimal)row.get("total")).add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount()));
            row.put("count", ((Long)row.get("count")) + 1L);
        }
        List<Expense> allExpenses = expenses.findAll();
        BigDecimal pendingExpenseAmount = allExpenses.stream().filter(e -> "PENDIENTE_APROBACION".equals(e.getExpenseStatus())).map(Expense::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingExpenseCount = allExpenses.stream().filter(e -> "PENDIENTE_APROBACION".equals(e.getExpenseStatus())).count();
        long todayPayments = validPayments.stream().filter(p -> p.getPaidAt()!=null && p.getPaidAt().toLocalDate().equals(java.time.LocalDate.now())).count();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("paymentMethods", new ArrayList<>(byMethod.values()));
        out.put("pendingExpenseAmount", pendingExpenseAmount);
        out.put("pendingExpenseCount", pendingExpenseCount);
        out.put("todayPaymentCount", todayPayments);
        return out;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public Payment registerPayment(@Valid @RequestBody Payment payment, Authentication authentication, HttpServletRequest request) {
        if (payment.getPatientId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un paciente");
        String auth = request.getHeader("Authorization");
        boolean proformaFlow = payment.getProformaId() != null;
        if (proformaFlow) {
            Map<String,Object> base = encounterClient.proformaBase(payment.getProformaId(), auth);
            if (!Objects.equals(longValue(base.get("patientId")), payment.getPatientId())) throw new ResponseStatusException(HttpStatus.CONFLICT,"La proforma no pertenece al paciente seleccionado");
            String status = text(base.get("status")).toUpperCase(Locale.ROOT);
            if (!Set.of("ACEPTADA","EN_TRATAMIENTO","COMPLETADA").contains(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"La proforma no está disponible para cobranza");
            BigDecimal total = decimal(base.get("totalAmount"));
            BigDecimal already = payments.findAllByProformaIdOrderByPaidAtDesc(payment.getProformaId()).stream().filter(p->!"ANULADO".equals(p.getPaymentStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal balance = total.subtract(already).max(BigDecimal.ZERO);
            if (payment.getAmount()==null||payment.getAmount().signum()<=0||payment.getAmount().compareTo(balance)>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El monto debe ser mayor a cero y no superar el saldo pendiente de la proforma");
            payment.setPatientName(text(base.get("patientName"))); payment.setProformaCode(text(base.get("code")));
            List<Map<String,Object>> lines=mapList(base.get("lines"));
            if (payment.getEncounterId()!=null) {
                Map<String,Object> visit = encounterClient.paymentContext(payment.getPatientId(), auth).stream()
                        .filter(x -> Objects.equals(longValue(x.get("encounterId")), payment.getEncounterId()))
                        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"La cita seleccionada no pertenece al paciente"));
                if (!Objects.equals(longValue(visit.get("proformaId")), payment.getProformaId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita seleccionada no pertenece a esta proforma");
                }
                String visitStatus = text(visit.get("status")).toUpperCase(Locale.ROOT);
                if (!Set.of("LLEGO","EN_ATENCION","FINALIZADA").contains(visitStatus)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo puedes asignar un pago a una cita cuando el paciente ya llegó. Para adelantos usa el plan o tratamiento sin seleccionar una cita.");
                }
                String visitLine = text(visit.get("proformaLineReference"));
                if (visitLine.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita seleccionada es de evaluación/origen y no representa una sesión del tratamiento. Usa adelanto general o selecciona una sesión clínica vinculada.");
                }
                if (payment.getProformaLineReference()!=null && !payment.getProformaLineReference().isBlank() && !payment.getProformaLineReference().equals(visitLine)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita seleccionada corresponde a otro tratamiento de la proforma");
                }
                payment.setAppointmentId(longValue(visit.get("appointmentId")));
                if (payment.getProformaLineReference()==null || payment.getProformaLineReference().isBlank()) payment.setProformaLineReference(visitLine);
                if (payment.getTreatmentName()==null || payment.getTreatmentName().isBlank()) payment.setTreatmentName(text(visit.get("treatmentName")));
            } else {
                // Un adelanto del plan o de una línea no se atribuye artificialmente a la cita de evaluación/origen.
                payment.setAppointmentId(null);
            }
            if (payment.getProformaLineReference()!=null&&!payment.getProformaLineReference().isBlank()) {
                Map<String,Object> line=lines.stream().filter(x->payment.getProformaLineReference().equals(text(x.get("lineReference")))).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"El tratamiento seleccionado ya no existe en la proforma"));
                BigDecimal lineTotal=line.get("acceptedAmount")==null?decimal(line.get("subtotal")):decimal(line.get("acceptedAmount"));
                BigDecimal linePaid=payments.findAllByProformaIdOrderByPaidAtDesc(payment.getProformaId()).stream()
                        .filter(x->!"ANULADO".equals(x.getPaymentStatus())&&payment.getProformaLineReference().equals(x.getProformaLineReference()))
                        .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(payment.getAmount().compareTo(lineTotal.subtract(linePaid).max(BigDecimal.ZERO))>0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El monto supera el saldo de este tratamiento. Usa adelanto general si el pago corresponde al plan completo.");
                payment.setTreatmentName(text(line.get("treatmentName")));
            } else payment.setTreatmentName(null);
            payment.setConcept(payment.getTreatmentName()!=null&&!payment.getTreatmentName().isBlank()?payment.getTreatmentName()+" · "+payment.getProformaCode():"Adelanto general · "+payment.getProformaCode());
        } else {
            if (payment.getEncounterId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un tratamiento con saldo pendiente");
            List<Map<String,Object>> pending = encounterClient.pendingTreatments(payment.getPatientId(), auth);
            Map<String,Object> target = pending.stream().filter(x -> Objects.equals(longValue(x.get("encounterId")), payment.getEncounterId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "El tratamiento seleccionado ya no tiene saldo pendiente o no corresponde al paciente"));
            BigDecimal balance = decimal(target.get("balance"));
            if (payment.getAmount()==null||payment.getAmount().signum()<=0||payment.getAmount().compareTo(balance)>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El monto debe ser mayor a cero y no superar el saldo pendiente");
            payment.setPatientName(text(target.get("patientName"))); payment.setConcept(text(target.get("treatmentName"))); payment.setTreatmentName(text(target.get("treatmentName"))); payment.setAppointmentId(longValue(target.get("appointmentId")));
        }
        payment.setId(null); payment.setPaymentStatus("REGISTRADO"); payment.setRegisteredBy(authentication.getName()); payment.setPaidAt(LocalDateTime.now());
        Payment saved = payments.save(payment);
        try {
            if (proformaFlow && saved.getEncounterId()!=null) encounterClient.linkProformaPayment(saved.getEncounterId(),saved.getAmount(),saved.getReference(),saved.getPaidAt(),auth);
            else if (!proformaFlow) encounterClient.reconcile(saved.getEncounterId(),saved.getAmount(),saved.getReference(),saved.getPaidAt(),auth);
        } catch (Exception ex) { payments.delete(saved); throw new ResponseStatusException(HttpStatus.CONFLICT,"No se pudo vincular el pago con la cita/tratamiento. Actualiza y vuelve a intentar."); }
        encounterClient.audit("Payment", saved.getId().toString(), "REGISTRAR", null, saved, saved.getConcept(), auth);
        return saved;
    }

    @GetMapping("/patients/{patientId}/account-summary")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Map<String,Object> patientAccount(@PathVariable Long patientId) {
        List<Payment> list = payments.findAllByPatientIdOrderByPaidAtDesc(patientId);
        BigDecimal paid = list.stream().filter(p -> !"ANULADO".equals(p.getPaymentStatus())).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("patientId",patientId,"totalPaid",paid,"paymentCount",list.size(),"payments",list);
    }

    @PatchMapping("/payments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Payment paymentStatus(@PathVariable Long id, @RequestParam String value, HttpServletRequest request) {
        Payment payment = payments.findById(id).orElseThrow(() -> notFound("Pago no encontrado"));
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("REGISTRADO", "ANULADO").contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de pago no permitido");
        String previous = payment.getPaymentStatus(); if (normalized.equalsIgnoreCase(previous)) return payment;
        payment.setPaymentStatus(normalized); Payment saved = payments.save(payment); String auth=request.getHeader("Authorization");
        try {
            BigDecimal delta = "ANULADO".equals(normalized)&&!"ANULADO".equals(previous)?saved.getAmount().negate():"REGISTRADO".equals(normalized)&&"ANULADO".equals(previous)?saved.getAmount():BigDecimal.ZERO;
            if (delta.signum()!=0 && saved.getEncounterId()!=null) {
                if (saved.getProformaId()!=null) encounterClient.linkProformaPayment(saved.getEncounterId(),delta,(delta.signum()<0?"ANULACIÓN ":"REACTIVACIÓN ")+saved.getReference(),saved.getPaidAt(),auth);
                else encounterClient.reconcile(saved.getEncounterId(),delta,(delta.signum()<0?"ANULACIÓN ":"REACTIVACIÓN ")+saved.getReference(),saved.getPaidAt(),auth);
            }
        } catch (Exception ex) { payment.setPaymentStatus(previous); payments.save(payment); throw new ResponseStatusException(HttpStatus.CONFLICT,"No se pudo conciliar el cambio de estado. El pago conserva su estado anterior."); }
        encounterClient.audit("Payment", saved.getId().toString(), "CAMBIAR_ESTADO", previous, normalized, "Cambio de estado de pago", auth);
        return saved;
    }

    @GetMapping("/cash-sessions")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public List<CashSession> cashSessions() { return cashSessions.findTop100ByOrderByOpenedAtDesc(); }

    @PostMapping("/cash-sessions/open")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public CashSession open(@Valid @RequestBody CashSession session, Authentication authentication, HttpServletRequest request) {
        cashSessions.findFirstByCashierAndCashStatusOrderByOpenedAtDesc(authentication.getName(), "ABIERTA")
                .ifPresent(value -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una caja abierta para este usuario"); });
        session.setId(null); session.setCashier(authentication.getName()); session.setOpenedAt(LocalDateTime.now());
        session.setCashStatus("ABIERTA"); session.setExpectedAmount(session.getOpeningAmount());
        session.setActualAmount(null); session.setDifference(null); session.setClosedAt(null);
        CashSession saved = cashSessions.save(session);
        encounterClient.audit("CashSession", saved.getId().toString(), "ABRIR", null, saved, "Apertura de caja", request.getHeader("Authorization"));
        return saved;
    }

    @PostMapping("/cash-sessions/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public CashSession close(@PathVariable Long id, @RequestBody CloseCashRequest request, HttpServletRequest httpRequest) {
        CashSession session = cashSessions.findById(id).orElseThrow(() -> notFound("Caja no encontrada"));
        if (!"ABIERTA".equals(session.getCashStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja ya está cerrada");
        BigDecimal expected = request.expectedAmount() == null ? session.getOpeningAmount() : request.expectedAmount();
        BigDecimal actual = request.actualAmount() == null ? BigDecimal.ZERO : request.actualAmount();
        session.setExpectedAmount(expected); session.setActualAmount(actual); session.setDifference(actual.subtract(expected));
        session.setNotes(request.notes()); session.setClosedAt(LocalDateTime.now()); session.setCashStatus("CERRADA");
        CashSession saved = cashSessions.save(session);
        encounterClient.audit("CashSession", saved.getId().toString(), "CERRAR", null, saved, request.notes(), httpRequest.getHeader("Authorization"));
        return saved;
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public List<Expense> expenses(Authentication authentication) {
        boolean reception = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECEPCION"));
        return reception ? expenses.findTop300ByRegisteredByOrderByRegisteredAtDesc(authentication.getName()) : expenses.findTop300ByOrderByRegisteredAtDesc();
    }

    @PostMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','FINANZAS')")
    public Expense expense(@Valid @RequestBody Expense expense, Authentication authentication, HttpServletRequest request) {
        expense.setId(null); expense.setRegisteredBy(authentication.getName());
        expense.setRegisteredAt(LocalDateTime.now()); expense.setExpenseStatus("PENDIENTE_APROBACION");
        Expense saved = expenses.save(expense);
        encounterClient.audit("Expense", saved.getId().toString(), "REGISTRAR", null, saved, saved.getDescription(), request.getHeader("Authorization"));
        return saved;
    }

    @PatchMapping("/expenses/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','FINANZAS')")
    public Expense expenseStatus(@PathVariable Long id, @RequestParam String value, HttpServletRequest request) {
        Expense expense = expenses.findById(id).orElseThrow(() -> notFound("Gasto no encontrado"));
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of("PENDIENTE_APROBACION", "APROBADO", "RECHAZADO", "OBSERVADO", "ANULADO").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de gasto no permitido");
        }
        String before = expense.getExpenseStatus(); expense.setExpenseStatus(normalized); Expense saved = expenses.save(expense);
        encounterClient.audit("Expense", saved.getId().toString(), "CAMBIAR_ESTADO", before, normalized, "Cambio de estado de gasto", request.getHeader("Authorization"));
        return saved;
    }

    private List<Map<String,Object>> safePaymentContext(Long patientId, String auth) {
        try { return encounterClient.paymentContext(patientId, auth); }
        catch (Exception ex) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> mapList(Object v){if(!(v instanceof List<?> list))return List.of();List<Map<String,Object>> out=new ArrayList<>();for(Object item:list)if(item instanceof Map<?,?>m){Map<String,Object>x=new LinkedHashMap<>();m.forEach((k,val)->x.put(String.valueOf(k),val));out.add(x);}return out;}
    private Long longValue(Object v){if(v==null||String.valueOf(v).isBlank()||"null".equalsIgnoreCase(String.valueOf(v)))return null;try{return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}catch(Exception e){return null;}}
    private BigDecimal decimal(Object v){if(v==null)return BigDecimal.ZERO;try{return new BigDecimal(String.valueOf(v));}catch(Exception e){return BigDecimal.ZERO;}}
    private String text(Object v){return v==null?"":String.valueOf(v);}

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    public record CloseCashRequest(BigDecimal expectedAmount, BigDecimal actualAmount, String notes) {}
}
