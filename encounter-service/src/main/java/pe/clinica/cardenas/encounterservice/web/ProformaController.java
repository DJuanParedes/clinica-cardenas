package pe.clinica.cardenas.encounterservice.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.encounterservice.domain.*;
import pe.clinica.cardenas.encounterservice.repository.*;
import pe.clinica.cardenas.encounterservice.service.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/proformas")
@RequiredArgsConstructor
public class ProformaController {
    // BORRADOR/ENTREGADA se conservan únicamente para leer información histórica de versiones anteriores.
    private static final Set<String> STATUSES = Set.of("BORRADOR","ENTREGADA","ACEPTADA","EN_TRATAMIENTO","COMPLETADA","CANCELADA");
    private static final Set<String> LINE_STATUSES = Set.of("PROPUESTO","ACEPTADO","EN_PROCESO","REALIZADO","CANCELADO");

    private final TreatmentProformaRepository proformas;
    private final ProformaHistoryRepository history;
    private final ClinicalEncounterRepository encounters;
    private final RemoteApiClient remote;
    private final AuditService audit;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<TreatmentProforma> list(@RequestParam(required = false) Long patientId,
                                        @RequestParam(defaultValue = "true") boolean financial,
                                        HttpServletRequest request) {
        List<TreatmentProforma> rows = patientId == null
                ? proformas.findTop300ByOrderByUpdatedAtDesc()
                : proformas.findAllByPatientIdOrderByUpdatedAtDesc(patientId);
        rows.forEach(this::normalizeLegacyLineMetadata);
        if (financial) rows.forEach(p -> hydrateFinancialSnapshot(p, request.getHeader(HttpHeaders.AUTHORIZATION)));
        return rows;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public TreatmentProforma get(@PathVariable Long id,
                                  @RequestParam(defaultValue = "true") boolean financial,
                                  HttpServletRequest request) {
        TreatmentProforma p = find(id);
        if (financial) hydrateFinancialSnapshot(p, request.getHeader(HttpHeaders.AUTHORIZATION));
        return p;
    }

    /** Base de facturación sin llamada de regreso a Finanzas; evita dependencias circulares. */
    @GetMapping("/{id}/billing-base")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public Map<String,Object> billingBase(@PathVariable Long id) {
        TreatmentProforma p = find(id);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", p.getId()); out.put("code", p.getCode()); out.put("patientId", p.getPatientId());
        out.put("patientName", p.getPatientName()); out.put("appointmentId", p.getAppointmentId());
        out.put("encounterId", p.getEncounterId()); out.put("doctorName", p.getDoctorName()); out.put("status", p.getStatus());
        List<ClinicalEncounter> linkedVisits = encounters.findAllByPatientIdOrderByScheduledStartDesc(p.getPatientId()).stream()
                .filter(e -> Objects.equals(e.getProformaId(), p.getId())).toList();
        List<Map<String,Object>> lineViews = new ArrayList<>();
        for (ProformaLine line : p.getLines() == null ? List.<ProformaLine>of() : p.getLines()) {
            List<ClinicalEncounter> lineVisits = linkedVisits.stream().filter(e -> Objects.equals(e.getProformaLineReference(), line.getLineReference())).toList();
            List<ClinicalEncounter> scheduled = lineVisits.stream().filter(e -> !Set.of("CANCELADA","NO_ASISTIO","REPROGRAMADA").contains(String.valueOf(e.getStatus()).toUpperCase(Locale.ROOT))).toList();
            Map<String,Object> view = new LinkedHashMap<>();
            view.put("lineReference", line.getLineReference()); view.put("treatmentName", line.getTreatmentName()); view.put("toothZone", line.getToothZone());
            view.put("quantity", line.getQuantity()); view.put("unitPrice", line.getUnitPrice()); view.put("subtotal", line.getSubtotal()); view.put("acceptedAmount", p.getAcceptedAmountForLine(line.getLineReference()));
            view.put("treatmentStatus", line.getTreatmentStatus()); view.put("plannedSessions", line.getPlannedSessions() == null ? 1 : line.getPlannedSessions());
            List<Long> pendingNoShowAppointmentIds = lineVisits.stream().filter(e -> "NO_ASISTIO".equalsIgnoreCase(String.valueOf(e.getStatus())))
                    .map(ClinicalEncounter::getAppointmentId).filter(Objects::nonNull).toList();
            view.put("scheduledSessions", scheduled.size()); view.put("completedSessions", lineVisits.stream().filter(e -> "FINALIZADA".equals(e.getStatus())).count());
            view.put("appointmentIds", scheduled.stream().map(ClinicalEncounter::getAppointmentId).filter(Objects::nonNull).toList());
            view.put("pendingNoShowAppointmentIds", pendingNoShowAppointmentIds);
            lineViews.add(view);
        }
        out.put("totalAmount", p.getTotalAmount()); out.put("lines", lineViews);
        out.put("acceptedAt", p.getAcceptedAt()); out.put("createdAt", p.getCreatedAt());
        return out;
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION','FINANZAS')")
    public List<ProformaHistory> history(@PathVariable Long id) {
        find(id);
        return history.findAllByProformaIdOrderByCreatedAtDesc(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    @Transactional
    public TreatmentProforma create(@RequestBody TreatmentProforma incoming, Authentication auth, HttpServletRequest request) {
        validateAndNormalize(incoming, true);
        incoming.setId(null);
        incoming.setCode("PRO-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().substring(0,8).toUpperCase());
        // Regla clínica actual: una proforma se registra únicamente cuando el paciente ya aceptó el plan.
        incoming.setStatus("ACEPTADA");
        incoming.setAcceptedAt(LocalDateTime.now());
        incoming.getLines().forEach(line -> line.setTreatmentStatus("ACEPTADO"));
        incoming.setCreatedBy(auth.getName());
        incoming.setCreatedAt(LocalDateTime.now());
        incoming.setUpdatedAt(LocalDateTime.now());
        ensureAppointmentAvailable(incoming.getAppointmentId(), null);
        TreatmentProforma saved = proformas.save(incoming);
        // El ID de la proforma ya existe; ahora puede quedar registrado en la atención clínica.
        attachAndSyncEncounter(saved, request.getHeader(HttpHeaders.AUTHORIZATION));
        saved = proformas.save(saved);
        addHistory(saved, "ACEPTADA", "Plan aceptado creado con " + saved.getLines().size() + " tratamiento(s).", auth);
        audit.log("TRATAMIENTOS_PROFORMAS","TreatmentProforma",saved.getId().toString(),"CREAR_ACEPTADA",auth,null,saved,"Plan de tratamiento aceptado");
        hydrateFinancialSnapshot(saved, request.getHeader(HttpHeaders.AUTHORIZATION));
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    @Transactional
    public TreatmentProforma update(@PathVariable Long id, @RequestBody TreatmentProforma incoming, Authentication auth, HttpServletRequest request) {
        TreatmentProforma current = find(id);
        hydrateFinancialSnapshot(current, request.getHeader(HttpHeaders.AUTHORIZATION));
        if (Set.of("COMPLETADA","CANCELADA").contains(current.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La proforma está cerrada y no puede editarse");
        }
        if (Boolean.TRUE.equals(current.getTraceabilityLocked())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, current.getTraceabilityLockReason()==null?"El plan aceptado ya tiene trazabilidad clínica o financiera y no puede modificarse.":current.getTraceabilityLockReason());
        }
        validateAndNormalize(incoming, false);
        if (!Objects.equals(current.getPatientId(), incoming.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar el paciente de una proforma ya creada. Crea una nueva proforma.");
        }
        if (current.getAppointmentId() != null && !Objects.equals(current.getAppointmentId(), incoming.getAppointmentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar la cita de origen de una proforma aceptada. La reprogramación de la cita conserva el vínculo automáticamente.");
        }
        if (current.getAppointmentId() == null && incoming.getAppointmentId() != null) ensureAppointmentAvailable(incoming.getAppointmentId(), id);
        TreatmentProforma before = snapshot(current);
        current.setPatientName(incoming.getPatientName());
        current.setAppointmentId(incoming.getAppointmentId());
        current.setDoctorName(incoming.getDoctorName());
        current.setDiscountAmount(incoming.getDiscountAmount());
        current.setAdjustmentAmount(incoming.getAdjustmentAmount());
        current.setAdjustmentReason(incoming.getAdjustmentReason());
        current.setAuthorizedBy(incoming.getAuthorizedBy());
        current.setNotes(incoming.getNotes());
        current.setLines(mergeLineReferences(current.getLines(), incoming.getLines()));
        current.setUpdatedAt(LocalDateTime.now());
        attachAndSyncEncounter(current, request.getHeader(HttpHeaders.AUTHORIZATION));
        TreatmentProforma saved = proformas.save(current);
        addHistory(saved, "ACTUALIZADA", "Se actualizaron tratamientos o importes manteniendo el plan aceptado y su trazabilidad.", auth);
        audit.log("TRATAMIENTOS_PROFORMAS","TreatmentProforma",id.toString(),"ACTUALIZAR",auth,before,saved,"Edición de proforma aceptada");
        hydrateFinancialSnapshot(saved, request.getHeader(HttpHeaders.AUTHORIZATION));
        return saved;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPCION')")
    @Transactional
    public TreatmentProforma status(@PathVariable Long id, @RequestParam String value, Authentication auth, HttpServletRequest request) {
        TreatmentProforma p = find(id);
        String next = normalizeStatus(value, null);
        if (Set.of("BORRADOR","ENTREGADA").contains(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las nuevas proformas son aceptadas desde su creación. No pueden volver a borrador o entregada.");
        }
        if (Set.of("EN_TRATAMIENTO","COMPLETADA").contains(next) && !next.equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El avance de la proforma se actualiza automáticamente desde las citas clínicas; no se cambia manualmente.");
        }
        if ("CANCELADA".equals(p.getStatus()) && !"CANCELADA".equals(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Una proforma cancelada conserva su historial y no puede reabrirse. Crea una nueva proforma.");
        }
        hydrateFinancialSnapshot(p, request.getHeader(HttpHeaders.AUTHORIZATION));
        if ("CANCELADA".equals(next) && !Boolean.TRUE.equals(p.getCancellationAllowed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, p.getCancellationBlockReason()==null?"La proforma no puede cancelarse mientras tenga pagos, tratamiento iniciado o citas activas.":p.getCancellationBlockReason());
        }
        String before = p.getStatus();
        p.setStatus(next);
        if ("ACEPTADA".equals(next) && p.getAcceptedAt() == null) p.setAcceptedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        if ("CANCELADA".equals(next)) clearEncounterForCancelledProforma(p);
        TreatmentProforma saved = proformas.save(p);
        addHistory(saved, "ESTADO", before + " → " + next, auth);
        audit.log("TRATAMIENTOS_PROFORMAS","TreatmentProforma",id.toString(),"CAMBIAR_ESTADO",auth,before,next,"Cambio de estado");
        hydrateFinancialSnapshot(saved, request.getHeader(HttpHeaders.AUTHORIZATION));
        return saved;
    }

    private boolean hasStartedTreatment(TreatmentProforma p) {
        if ("EN_TRATAMIENTO".equals(p.getStatus()) || "COMPLETADA".equals(p.getStatus())) return true;
        if (p.getLines() != null && p.getLines().stream().anyMatch(l -> Set.of("EN_PROCESO","REALIZADO").contains(l.getTreatmentStatus()))) return true;
        return encounters.findAll().stream().anyMatch(e -> Objects.equals(e.getProformaId(), p.getId()) && e.getProformaLineReference() != null && !e.getProformaLineReference().isBlank() && Set.of("EN_ATENCION","FINALIZADA").contains(e.getStatus()));
    }

    private void validateAndNormalize(TreatmentProforma p, boolean creating) {
        if (p.getPatientId() == null || blank(p.getPatientName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un paciente");
        if (p.getLines() == null || p.getLines().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un tratamiento a la proforma");
        List<ProformaLine> clean = new ArrayList<>();
        for (ProformaLine line : p.getLines()) {
            if (line == null || blank(line.getTreatmentName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada línea debe tener un tratamiento");
            int qty = line.getQuantity() == null ? 0 : line.getQuantity();
            if (qty <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
            int sessions = line.getPlannedSessions() == null ? 1 : line.getPlannedSessions();
            if (sessions <= 0 || sessions > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las sesiones planificadas deben estar entre 1 y 100");
            line.setPlannedSessions(sessions);
            BigDecimal unit = nvl(line.getUnitPrice());
            if (unit.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio unitario no puede ser negativo");
            line.setLineReference(blank(line.getLineReference()) ? UUID.randomUUID().toString() : line.getLineReference().trim());
            line.setTreatmentName(line.getTreatmentName().trim());
            line.setToothZone(blank(line.getToothZone()) ? null : line.getToothZone().trim());
            line.setUnitPrice(unit);
            line.setTreatmentStatus(creating ? "ACEPTADO" : normalizeLineStatus(line.getTreatmentStatus(), "ACEPTADO"));
            clean.add(line);
        }
        p.setLines(clean);
        p.setPatientName(p.getPatientName().trim());
        p.setDoctorName(blank(p.getDoctorName()) ? null : p.getDoctorName().trim());
        p.setDiscountAmount(nvl(p.getDiscountAmount()));
        p.setAdjustmentAmount(nvl(p.getAdjustmentAmount()));
        p.setAdjustmentReason(blank(p.getAdjustmentReason()) ? null : p.getAdjustmentReason().trim());
        p.setAuthorizedBy(blank(p.getAuthorizedBy()) ? null : p.getAuthorizedBy().trim());
        if ((p.getDiscountAmount().signum() > 0 || p.getAdjustmentAmount().signum() != 0) && (blank(p.getAdjustmentReason()) || blank(p.getAuthorizedBy())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los descuentos o ajustes requieren motivo y responsable autorizado");
        if (p.getDiscountAmount().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento no puede ser negativo");
        if (p.getTotalAmount().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El total de la proforma debe ser mayor a cero");
    }

    private List<ProformaLine> mergeLineReferences(List<ProformaLine> oldLines, List<ProformaLine> incoming) {
        Map<String, ProformaLine> oldByRef = new HashMap<>();
        if (oldLines != null) for (ProformaLine l : oldLines) if (!blank(l.getLineReference())) oldByRef.put(l.getLineReference(), l);
        List<ProformaLine> out = new ArrayList<>();
        for (ProformaLine line : incoming) {
            if (blank(line.getLineReference())) line.setLineReference(UUID.randomUUID().toString());
            ProformaLine previous = oldByRef.get(line.getLineReference());
            // El estado de progreso no se edita desde el formulario; se conserva el estado clínico existente.
            if (previous != null) line.setTreatmentStatus(previous.getTreatmentStatus());
            else line.setTreatmentStatus("ACEPTADO");
            out.add(line);
        }
        return out;
    }

    private void ensureAppointmentAvailable(Long appointmentId, Long selfId) {
        if (appointmentId == null) return;
        boolean used = proformas.findAllByAppointmentIdOrderByUpdatedAtDesc(appointmentId).stream()
                .anyMatch(x -> !Objects.equals(x.getId(), selfId) && !"CANCELADA".equals(x.getStatus()));
        if (used) throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya está vinculada a otra proforma activa. Abre esa proforma para editarla.");
    }

    private void clearEncounterForCancelledProforma(TreatmentProforma p) {
        encounters.findAll().stream().filter(e -> Objects.equals(e.getProformaId(), p.getId())).forEach(e -> {
            if (Set.of("EN_ATENCION","FINALIZADA").contains(e.getStatus())) return;
            e.setProformaId(null); e.setProformaCode(null); e.setProformaLineReference(null); e.setPlannedTreatmentAmount(null);
            e.setUpdatedAt(LocalDateTime.now()); encounters.save(e);
        });
    }

    private Map<String,Object> requireAppointment(Long appointmentId, String authHeader) {
        try { return remote.appointment(appointmentId, authHeader); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cita vinculada no existe o no es accesible"); }
    }

    private void attachAndSyncEncounter(TreatmentProforma p, String authHeader) {
        if (p.getAppointmentId() == null) { p.setEncounterId(null); return; }
        Map<String,Object> a = requireAppointment(p.getAppointmentId(), authHeader);
        Long appointmentPatient = longValue(a.get("patientId"));
        if (!Objects.equals(appointmentPatient, p.getPatientId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita seleccionada no pertenece al paciente de la proforma");
        ClinicalEncounter e = encounters.findByAppointmentId(p.getAppointmentId()).orElseGet(() -> ClinicalEncounter.builder()
                .code("ATE-"+Year.now().getValue()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .appointmentId(p.getAppointmentId()).patientId(appointmentPatient).patientName(str(a.get("patientName")))
                .doctorName(str(a.get("doctorName"))).doctorAccountEmail(normalizeEmail(str(a.get("doctorAccountEmail"))))
                .chairName(str(a.get("chair"))).specialty(str(a.get("specialty")))
                .scheduledStart(LocalDateTime.parse(str(a.get("startAt")))).scheduledEnd(LocalDateTime.parse(str(a.get("endAt"))))
                .status(strOr(a.get("status"),"PROGRAMADA")).treatmentNames(str(a.get("treatmentName")))
                .durationMinutes(intValue(a.get("durationMinutes"),60)).productionAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).visitDayPaidAmount(BigDecimal.ZERO).paymentStatus("SIN_PAGO")
                .labRepetition(false).correctiveTreatment(false).postTreatmentEmergency(false).followUpCompleted(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        e.setProformaId(p.getId()); e.setProformaCode(p.getCode());
        // La cita de origen representa evaluación/aceptación. No reconoce como producción el total planificado.
        e.setPlannedTreatmentAmount(null);
        // La cita de origen es evaluación/aceptación; no se presenta como una sesión de ninguna línea del plan.
        e.setProformaLineReference(null);
        e.setUpdatedAt(LocalDateTime.now());
        ClinicalEncounter savedEncounter = encounters.save(e);
        p.setEncounterId(savedEncounter.getId());
        if (blank(p.getDoctorName())) p.setDoctorName(savedEncounter.getDoctorName());
    }

    private void hydrateFinancialSnapshot(TreatmentProforma p, String authHeader) {
        BigDecimal paid = BigDecimal.ZERO;
        try {
            Map<String,Object> summary = remote.proformaPayments(p.getId(), authHeader);
            if (summary != null && summary.get("totalPaid") != null) paid = new BigDecimal(String.valueOf(summary.get("totalPaid")));
        } catch (Exception ignored) {
            // Compatibilidad offline: usa pagos conciliados en atenciones antiguas si Finanzas no responde.
            paid = encounters.findAll().stream().filter(e -> Objects.equals(e.getProformaId(), p.getId()))
                    .map(ClinicalEncounter::getPaidAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        p.setPaidAmount(paid);
        List<ClinicalEncounter> treatmentLinks = encounters.findAll().stream().filter(e -> Objects.equals(e.getProformaId(), p.getId()) && e.getProformaLineReference()!=null && !e.getProformaLineReference().isBlank()).toList();
        boolean hasTreatmentLink = !treatmentLinks.isEmpty();
        boolean started = hasStartedTreatment(p);
        boolean hasActiveTreatmentAppointment = treatmentLinks.stream().anyMatch(e -> !"CANCELADA".equalsIgnoreCase(String.valueOf(e.getStatus())));
        boolean locked = paid.signum() > 0 || hasTreatmentLink || started;
        p.setTraceabilityLocked(locked);
        if (paid.signum() > 0) p.setTraceabilityLockReason("El plan ya registra pagos. Para conservar importes y trazabilidad, crea una nueva proforma si necesitas cambiar el plan.");
        else if (hasTreatmentLink) p.setTraceabilityLockReason("El plan ya tiene historial de citas/sesiones vinculado. Sus líneas e importes quedan protegidos para no romper la trazabilidad.");
        else if (started) p.setTraceabilityLockReason("El tratamiento ya inició. El plan queda congelado para conservar citas, importes y pagos.");
        else p.setTraceabilityLockReason(null);
        boolean cancellationAllowed = paid.signum()==0 && !started && !hasActiveTreatmentAppointment;
        p.setCancellationAllowed(cancellationAllowed);
        if (paid.signum()>0) p.setCancellationBlockReason("La proforma tiene pagos registrados. Anula o regulariza los pagos en Caja antes de cancelarla.");
        else if (started) p.setCancellationBlockReason("El tratamiento ya inició. Conserva la proforma EN_TRATAMIENTO para mantener citas y pagos realizados; no la canceles.");
        else if (hasActiveTreatmentAppointment) p.setCancellationBlockReason("La proforma tiene citas de tratamiento activas o una inasistencia pendiente de resolver. Cierra/reprograma esas citas antes de cancelar el plan.");
        else p.setCancellationBlockReason(null);
    }

    private void addHistory(TreatmentProforma p, String action, String detail, Authentication auth) {
        history.save(ProformaHistory.builder().proformaId(p.getId()).action(action).detail(detail)
                .actor(auth == null ? "SYSTEM" : auth.getName()).createdAt(LocalDateTime.now()).build());
    }

    private TreatmentProforma find(Long id) { TreatmentProforma p=proformas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Proforma no encontrada")); return normalizeLegacyLineMetadata(p); }
    private TreatmentProforma normalizeLegacyLineMetadata(TreatmentProforma p) { boolean changed=false; if(p.getLines()!=null){for(ProformaLine line:p.getLines()){if(line==null)continue;if(blank(line.getLineReference())){line.setLineReference(UUID.randomUUID().toString());changed=true;}if(line.getPlannedSessions()==null||line.getPlannedSessions()<=0){line.setPlannedSessions(1);changed=true;}}}return changed?proformas.save(p):p; }
    private String normalizeStatus(String value, String fallback) { String s = blank(value) ? fallback : value.trim().toUpperCase(); if (s == null || !STATUSES.contains(s)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado de proforma no permitido"); return s; }
    private String normalizeLineStatus(String value, String fallback) { String s = blank(value) ? fallback : value.trim().toUpperCase(); if (!LINE_STATUSES.contains(s)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado de tratamiento no permitido"); return s; }
    private TreatmentProforma snapshot(TreatmentProforma p) { return TreatmentProforma.builder().id(p.getId()).code(p.getCode()).patientId(p.getPatientId()).patientName(p.getPatientName()).appointmentId(p.getAppointmentId()).encounterId(p.getEncounterId()).doctorName(p.getDoctorName()).status(p.getStatus()).discountAmount(p.getDiscountAmount()).adjustmentAmount(p.getAdjustmentAmount()).adjustmentReason(p.getAdjustmentReason()).authorizedBy(p.getAuthorizedBy()).notes(p.getNotes()).createdBy(p.getCreatedBy()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).acceptedAt(p.getAcceptedAt()).lines(p.getLines()==null?List.of():p.getLines().stream().map(l->ProformaLine.builder().lineReference(l.getLineReference()).treatmentName(l.getTreatmentName()).toothZone(l.getToothZone()).quantity(l.getQuantity()).plannedSessions(l.getPlannedSessions()).unitPrice(l.getUnitPrice()).treatmentStatus(l.getTreatmentStatus()).build()).toList()).build(); }
    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private String strOr(Object v, String d) { String s=str(v); return s.isBlank()?d:s; }
    private Long longValue(Object v) { return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v)); }
    private Integer intValue(Object v, int d) { return v == null ? d : (v instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(v))); }
    private String normalizeEmail(String value) { return blank(value) ? null : value.trim().toLowerCase(); }
}
