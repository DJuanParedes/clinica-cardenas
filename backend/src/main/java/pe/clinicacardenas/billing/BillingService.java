package pe.clinicacardenas.billing;

import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.clinicacardenas.clinical.*;
import pe.clinicacardenas.common.ApiException;
import pe.clinicacardenas.common.AuditService;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.patient.PatientRepository;
import pe.clinicacardenas.security.CurrentUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BillingService {
    private static final String RECHAZADO = "RECHAZADO";
    private static final String CUSTOMERDOCNUMBER = "customerDocNumber";
    private static final String CUSTOMERDOCTYPE = "customerDocType";
    private static final String ITEMS = "items";


    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final InvoiceSeriesRepository series;
    private final SunatSubmissionRepository submissions;
    private final PatientRepository patients;
    private final TreatmentPlanRepository plans;
    private final TreatmentItemRepository treatmentItems;
    private final SunatGateway gateway;
    private final XmlSigner signer;
    private final AuditService audit;
    private final UblBuilder.Company company;
    private final BigDecimal igvRate;
    private final BigDecimal boletaLimit;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public BillingService(InvoiceRepository invoices, InvoiceItemRepository invoiceItems, InvoiceSeriesRepository series,
                          SunatSubmissionRepository submissions, PatientRepository patients,
                          TreatmentPlanRepository plans, TreatmentItemRepository treatmentItems,
                          SunatGateway gateway, XmlSigner signer, AuditService audit,
                          @Value("${app.company.ruc}") String ruc,
                          @Value("${app.company.business-name}") String businessName,
                          @Value("${app.company.trade-name}") String tradeName,
                          @Value("${app.company.address}") String address,
                          @Value("${app.company.ubigeo}") String ubigeo,
                          @Value("${app.sunat.igv-rate}") BigDecimal igvRate,
                          @Value("${app.sunat.boleta-max-sin-documento}") BigDecimal boletaLimit) {
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.series = series;
        this.submissions = submissions;
        this.patients = patients;
        this.plans = plans;
        this.treatmentItems = treatmentItems;
        this.gateway = gateway;
        this.signer = signer;
        this.audit = audit;
        this.company = new UblBuilder.Company(ruc, businessName, tradeName, address, ubigeo);
        this.igvRate = igvRate;
        this.boletaLimit = boletaLimit;
    }

    public record LineRequest(String description, BigDecimal quantity, BigDecimal unitPrice, Long treatmentItemId) {}

    public record InvoiceRequest(String docType, Long patientId, String customerDocType, String customerDocNumber,
                                 String customerName, String customerAddress, List<LineRequest> items) {}

    public UblBuilder.Company company() {
        return company;
    }

    public record SeriesRequest(String docType, String series, Boolean active) {}

    public List<InvoiceSeries> allSeries() {
        return series.findAll(org.springframework.data.domain.Sort.by("docType", "series"));
    }

    /**
     * Crea o edita una serie. SUNAT: boletas (03) empiezan con B y facturas (01) con F, 4 caracteres (ej. B001).
     * Si la serie ya emitió comprobantes no se cambia el código ni el tipo: solo se activa o desactiva.
     */
    @Transactional
    public InvoiceSeries saveSeries(Long id, SeriesRequest r) {
        InvoiceSeries s = id == null ? new InvoiceSeries() : series.findById(id).orElseThrow(() -> ApiException.notFound("La serie"));
        String type = r.docType() == null ? s.docType : r.docType().trim();
        String code = r.series() == null ? s.series : r.series().trim().toUpperCase();
        if (!"01".equals(type) && !"03".equals(type) && !"07".equals(type)) throw ApiException.field("docType", "Elige factura, boleta o nota de crédito.");
        String prefix = "01".equals(type) ? "F" : "03".equals(type) ? "B" : "[BF]";
        if ("07".equals(type) && (code == null || !code.matches("^[BF][A-Z0-9]{3}$")))
            throw ApiException.field("series", "La serie de notas de crédito tiene 4 caracteres y empieza con B (para boletas) o F (para facturas), ej. BC01.");
        if (code == null || !code.matches("^" + prefix + "[A-Z0-9]{3}$"))
            throw ApiException.field("series", ("01".equals(type) ? "La serie de facturas" : "La serie de boletas") + " debe tener 4 caracteres y empezar con " + prefix + " (ej. " + prefix + "001).");
        if (id != null && s.lastNumber > 0 && (!code.equals(s.series) || !type.equals(s.docType)))
            throw ApiException.conflict("SERIE_USADA", "La serie ya emitió comprobantes: no se puede cambiar su código ni su tipo. Puedes desactivarla y crear otra.");
        if (series.findAll().stream().anyMatch(x -> x.series.equals(code) && !x.id.equals(id)))
            throw ApiException.field("series", "Ya existe la serie " + code + ".");
        s.docType = type;
        s.series = code;
        if (r.active() != null) s.active = r.active();
        series.save(s);
        audit.register("SERIE", s.id, id == null ? "CREAR" : "EDITAR", type + " " + code + (s.active ? "" : " (inactiva)"));
        return s;
    }

    public List<InvoiceSeries> activeSeries() {
        return series.findByActiveTrueOrderBySeriesAsc();
    }

    // ------------------------------------------------------------------ HU-10 generar comprobante

    /** Procedimientos realizados y aún no facturados del paciente. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> billableItems(Long patientId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TreatmentPlan p : plans.findByPatientIdOrderByCreatedAtDesc(patientId)) {
            if ("ANULADO".equals(p.status)) continue;
            for (TreatmentItem i : treatmentItems.findByPlanIdOrderBySortOrderAscIdAsc(p.id)) {
                if (i.invoiceId == null && "REALIZADO".equals(i.status)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("treatmentItemId", i.id);
                    m.put("description", i.procedure.name + (i.tooth == null ? "" : " - pieza " + i.tooth));
                    m.put("price", i.price);
                    m.put("planTitle", p.title);
                    m.put("doneAt", i.doneAt);
                    out.add(m);
                }
            }
        }
        return out;
    }

    private Map<String, String> validate(InvoiceRequest r, BigDecimal total) {
        Map<String, String> e = new LinkedHashMap<>();
        String type = r.docType();
        String cdt = r.customerDocType() == null ? "" : r.customerDocType();
        String doc = r.customerDocNumber() == null ? "" : r.customerDocNumber().trim();
        if (!"01".equals(type) && !"03".equals(type)) {
            e.put("docType", "Selecciona boleta o factura.");
            return e;
        }
        if ("01".equals(type)) {
            if (!"6".equals(cdt)) e.put(CUSTOMERDOCTYPE, "La factura se emite solo a un RUC.");
            else if (!RucValidator.isValid(doc)) e.put(CUSTOMERDOCNUMBER, "El RUC no es válido (11 dígitos y dígito verificador).");
            if (r.customerAddress() == null || r.customerAddress().isBlank()) e.put("customerAddress", "Ingresa la dirección fiscal del cliente.");
        } else {
            switch (cdt) {
                case "1" -> { if (!doc.matches("^\\d{8}$")) e.put(CUSTOMERDOCNUMBER, "El DNI debe tener 8 dígitos."); }
                case "4" -> { if (!doc.matches("^\\d{9,12}$")) e.put(CUSTOMERDOCNUMBER, "Carné de extranjería inválido."); }
                case "7" -> { if (!doc.matches("^[A-Za-z0-9]{6,12}$")) e.put(CUSTOMERDOCNUMBER, "Pasaporte inválido."); }
                case "6" -> { if (!RucValidator.isValid(doc)) e.put(CUSTOMERDOCNUMBER, "El RUC no es válido."); }
                case "0" -> {
                    if (total != null && total.compareTo(boletaLimit) > 0) {
                        e.put(CUSTOMERDOCTYPE, "Las boletas mayores a S/ " + boletaLimit.toPlainString() + " requieren identificar al cliente.");
                    }
                }
                default -> e.put(CUSTOMERDOCTYPE, "Selecciona el tipo de documento del cliente.");
            }
        }
        if (!"0".equals(cdt) && (r.customerName() == null || r.customerName().trim().length() < 3)) {
            e.put("customerName", "Ingresa el nombre o razón social del cliente.");
        }
        if (r.items() == null || r.items().isEmpty()) {
            e.put(ITEMS, "Agrega al menos un concepto.");
        } else {
            for (LineRequest l : r.items()) {
                if (l.description() == null || l.description().isBlank()) { e.put(ITEMS, "Cada concepto necesita una descripción."); break; }
                if (l.quantity() == null || l.quantity().signum() <= 0) { e.put(ITEMS, "La cantidad debe ser mayor a cero."); break; }
                if (l.unitPrice() == null || l.unitPrice().signum() <= 0) { e.put(ITEMS, "El precio debe ser mayor a cero."); break; }
            }
        }
        return e;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> create(InvoiceRequest r) {
        List<InvoiceCalculator.LineIn> lines = new ArrayList<>();
        if (r.items() != null) {
            for (LineRequest l : r.items()) {
                if (l.quantity() != null && l.unitPrice() != null && l.description() != null) {
                    lines.add(new InvoiceCalculator.LineIn(l.description().trim(), l.quantity(), l.unitPrice()));
                }
            }
        }
        InvoiceCalculator.Totals totals = InvoiceCalculator.compute(lines, igvRate);
        Map<String, String> errors = validate(r, totals.total());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Revisa los datos del comprobante.", errors);
        }
        Patient patient = null;
        if (r.patientId() != null) {
            patient = patients.findById(r.patientId()).orElseThrow(() -> ApiException.notFound("El paciente"));
        }
        // Treatment items: deben pertenecer al paciente y no estar facturados.
        List<TreatmentItem> linked = new ArrayList<>();
        for (LineRequest l : r.items()) {
            if (l.treatmentItemId() == null) { linked.add(null); continue; }
            TreatmentItem ti = treatmentItems.findById(l.treatmentItemId())
                    .orElseThrow(() -> ApiException.notFound("El procedimiento a facturar"));
            TreatmentPlan plan = plans.findById(ti.planId).orElseThrow(() -> ApiException.notFound("El plan"));
            if (patient == null || !plan.patientId.equals(patient.id)) {
                throw ApiException.badRequest("ITEM_AJENO", "Un procedimiento no pertenece al paciente seleccionado.");
            }
            if (ti.invoiceId != null) {
                throw ApiException.conflict("ITEM_FACTURADO", "El procedimiento \"" + ti.procedure.name + "\" ya fue facturado.");
            }
            linked.add(ti);
        }

        String seriesCode = "01".equals(r.docType()) ? "F001" : "B001";
        InvoiceSeries s = series.lockBySeries(seriesCode)
                .orElseThrow(() -> ApiException.badRequest("SERIE_INACTIVA", "La serie " + seriesCode + " no está habilitada."));
        s.lastNumber = s.lastNumber + 1;
        series.save(s);

        LocalDateTime now = Dates.now();
        Invoice inv = new Invoice();
        inv.docType = r.docType();
        inv.series = s.series;
        inv.number = s.lastNumber;
        inv.issueDate = now.toLocalDate();
        inv.issueTime = now.toLocalTime().truncatedTo(ChronoUnit.SECONDS);
        inv.patientId = patient == null ? null : patient.id;
        inv.customerDocType = r.customerDocType();
        boolean anonymous = "0".equals(r.customerDocType());
        inv.customerDocNumber = anonymous ? "-" : r.customerDocNumber().trim().toUpperCase();
        inv.customerName = anonymous ? "CLIENTES VARIOS" : r.customerName().trim().toUpperCase();
        inv.customerAddress = r.customerAddress() == null || r.customerAddress().isBlank() ? null : r.customerAddress().trim();
        inv.taxableAmount = totals.taxable();
        inv.igvAmount = totals.igv();
        inv.totalAmount = totals.total();
        inv.status = "GENERADO";
        inv.createdAt = now;
        inv.createdBy = CurrentUser.usernameOrSystem();
        invoices.save(inv);

        List<InvoiceItem> saved = new ArrayList<>();
        int n = 1;
        for (int idx = 0; idx < totals.lines().size(); idx++) {
            InvoiceCalculator.LineOut lo = totals.lines().get(idx);
            InvoiceItem it = new InvoiceItem();
            it.invoiceId = inv.id;
            it.lineNumber = n++;
            it.description = lo.description();
            it.quantity = lo.quantity();
            it.unitPrice = lo.unitPrice();
            it.unitValue = lo.unitValue();
            it.lineValue = lo.lineValue();
            it.lineIgv = lo.lineIgv();
            it.lineTotal = lo.lineTotal();
            TreatmentItem ti = linked.get(idx);
            if (ti != null) {
                it.treatmentItemId = ti.id;
                ti.invoiceId = inv.id;
                treatmentItems.save(ti);
                // v2.19.1: los cobros de ese procedimiento quedan amarrados al comprobante (no se anulan a escondidas).
                jdbc.update("update payments set invoice_id = ? where treatment_item_id = ? and status = 'VALIDO' and invoice_id is null",
                        inv.id, ti.id);
            }
            saved.add(invoiceItems.save(it));
        }
        // XML UBL 2.1 firmado digitalmente; el DigestValue de la firma es el "código hash" del comprobante.
        XmlSigner.Signed signed = signer.sign(UblBuilder.build(company, inv, saved));
        inv.xmlContent = signed.xml();
        inv.hash = signed.digestValue();
        invoices.save(inv);
        audit.register("COMPROBANTE", inv.id, "GENERAR", inv.getFullNumber() + " · S/ " + inv.totalAmount.toPlainString());
        return detailInternal(inv.id);
    }

    // ------------------------------------------------------------------ HU-11 envío a SUNAT

    @Transactional
    public Map<String, Object> send(Long id) {
        Invoice inv = get(id);
        if ("ACEPTADO".equals(inv.status) || "OBSERVADO".equals(inv.status)) {
            throw ApiException.conflict("YA_ENVIADO", "El comprobante ya fue aceptado por SUNAT.");
        }
        if (RECHAZADO.equals(inv.status)) {
            throw ApiException.conflict(RECHAZADO, "El comprobante fue rechazado. Emite uno nuevo con los datos corregidos.");
        }
        long t0 = System.currentTimeMillis();
        SunatGateway.Response res;
        try {
            res = gateway.send(inv, inv.xmlContent);
        } catch (Exception e) {
            res = new SunatGateway.Response("ERROR", null, "Sin respuesta del servicio: " + e.getMessage(), null, "SUNAT");
        }
        apply(inv, res, (int) (System.currentTimeMillis() - t0), "ENVIO_SUNAT");
        return detailInternal(inv.id);
    }

    /**
     * Consulta en SUNAT el estado del comprobante (getStatusCdr) y, si tiene constancia, actualiza el
     * estado local. Útil cuando un envío quedó en ERROR porque se cortó la conexión después de que
     * SUNAT ya lo había recibido.
     */
    @Transactional
    public Map<String, Object> checkStatus(Long id) {
        Invoice inv = get(id);
        long t0 = System.currentTimeMillis();
        Optional<SunatGateway.Response> res;
        try {
            res = gateway.status(inv);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SUNAT_NO_DISPONIBLE",
                    "No se pudo consultar a SUNAT: " + e.getMessage() + ". Intenta nuevamente en unos minutos.");
        }
        Map<String, Object> m;
        if (res.isPresent()) {
            String before = inv.status;
            apply(inv, res.get(), (int) (System.currentTimeMillis() - t0), "CONSULTA_SUNAT");
            m = detailInternal(inv.id);
            m.put("statusMessage", before.equals(inv.status)
                    ? "SUNAT confirma el estado " + inv.status + "."
                    : "SUNAT tiene registrado el comprobante como " + inv.status + ". Se actualizó el estado y se guardó la constancia.");
        } else {
            m = detailInternal(inv.id);
            m.put("statusMessage", "SUNAT no tiene registrado este comprobante. "
                    + ("GENERADO".equals(inv.status) || "ERROR".equals(inv.status) ? "Envíalo para obtener la constancia." : ""));
        }
        return m;
    }

    /** Consulta RUC en el padrón de SUNAT (para autocompletar al cliente de una factura). */
    public Map<String, Object> lookupRuc(String ruc) {
        if (!RucValidator.isValid(ruc)) {
            throw ApiException.badRequest("RUC_INVALIDO", "El RUC no es válido: revisa los 11 dígitos.");
        }
        try {
            return gateway.lookupRuc(ruc).orElseThrow(() -> ApiException.notFound("El RUC " + ruc + " en el padrón de SUNAT"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SUNAT_NO_DISPONIBLE",
                    "La consulta RUC no está disponible. Puedes escribir los datos del cliente manualmente.");
        }
    }

    private void apply(Invoice inv, SunatGateway.Response res, int ms, String action) {
        SunatSubmission sub = new SunatSubmission();
        sub.invoiceId = inv.id;
        sub.attemptAt = Dates.now();
        sub.environment = res.environment();
        sub.status = res.status();
        sub.responseCode = res.code();
        sub.responseDescription = res.description() != null && res.description().length() > 480
                ? res.description().substring(0, 480) : res.description();
        sub.durationMs = ms;
        sub.createdBy = CurrentUser.usernameOrSystem();
        submissions.save(sub);

        String previous = inv.status;
        inv.status = res.status();
        inv.sunatCode = res.code();
        inv.sunatDescription = sub.responseDescription;
        if (res.cdrXml() != null) inv.cdrContent = res.cdrXml();
        inv.sentAt = sub.attemptAt;
        invoices.save(inv);
        boolean nowAccepted = "ACEPTADO".equals(inv.status) || "OBSERVADO".equals(inv.status);
        if ("07".equals(inv.docType) && nowAccepted && inv.refInvoiceId != null) annulReferenced(inv);
        if (RECHAZADO.equals(inv.status) && !RECHAZADO.equals(previous)) {
            // libera los procedimientos para que puedan facturarse en un comprobante corregido
            for (InvoiceItem it : invoiceItems.findByInvoiceIdOrderByLineNumberAsc(inv.id)) {
                if (it.treatmentItemId != null) {
                    treatmentItems.findById(it.treatmentItemId).ifPresent(ti -> { ti.invoiceId = null; treatmentItems.save(ti); });
                }
            }
            jdbc.update("update payments set invoice_id = null where invoice_id = ?", inv.id);
        }
        audit.register("COMPROBANTE", inv.id, action, inv.getFullNumber() + " → " + res.status()
                + (res.code() == null ? "" : " (" + res.code() + ")"));
    }

    // ------------------------------------------------------------------ v2.19 nota de crédito

    /** Catálogo 09 de SUNAT: motivos de nota de crédito que anulan todo el comprobante. */
    public static final Map<String, String> NOTE_CODES = Map.of(
            "01", "Anulación de la operación",
            "02", "Anulación por error en el RUC",
            "06", "Devolución total");

    public record CreditNoteRequest(String code, String reason) {}

    /**
     * Emite una nota de crédito (tipo 07) que anula por completo un comprobante aceptado por SUNAT y la envía.
     * Cuando SUNAT la acepta, el comprobante original queda ANULADO y se liberan sus procedimientos y cobros.
     */
    @Transactional
    public Map<String, Object> creditNote(Long invoiceId, CreditNoteRequest r) {
        Invoice orig = get(invoiceId);
        if ("07".equals(orig.docType)) throw ApiException.badRequest("NOTA_SOBRE_NOTA", "No se emite una nota de crédito sobre otra nota de crédito.");
        if (!"ACEPTADO".equals(orig.status) && !"OBSERVADO".equals(orig.status))
            throw ApiException.conflict("NO_ACEPTADO", "Solo se anula con nota de crédito un comprobante aceptado por SUNAT. "
                    + ("ANULADO".equals(orig.status) ? "Este ya está anulado." : "Si no se envió o fue rechazado, no hace falta anularlo."));
        String code = r == null || r.code() == null ? "01" : r.code().trim();
        if (!NOTE_CODES.containsKey(code)) throw ApiException.field("code", "Elige el motivo de la nota de crédito.");
        String reason = r.reason() == null ? "" : r.reason().trim();
        if (reason.length() < 5) throw ApiException.field("reason", "Describe el motivo (mínimo 5 caracteres).");
        if (reason.length() > 250) throw ApiException.field("reason", "El motivo admite hasta 250 caracteres.");
        if ("02".equals(code) && !"01".equals(orig.docType)) throw ApiException.field("code", "«Error en el RUC» solo aplica a facturas.");
        Long pending = jdbc.queryForObject("""
                select count(*) from invoices where ref_invoice_id = ? and doc_type = '07' and status not in ('RECHAZADO')""", Long.class, invoiceId);
        if (pending != null && pending > 0) throw ApiException.conflict("YA_TIENE_NOTA", "Este comprobante ya tiene una nota de crédito emitida.");

        String prefix = "01".equals(orig.docType) ? "F" : "B";
        InvoiceSeries s = series.findAll().stream().filter(x -> "07".equals(x.docType) && x.active && x.series.startsWith(prefix))
                .findFirst().map(x -> series.lockBySeries(x.series).orElse(x))
                .orElseThrow(() -> ApiException.badRequest("SERIE_INACTIVA", "No hay una serie de notas de crédito activa que empiece con " + prefix + " (ej. " + prefix + "C01)."));
        s.lastNumber = s.lastNumber + 1;
        series.save(s);

        LocalDateTime now = Dates.now();
        Invoice nc = new Invoice();
        nc.docType = "07";
        nc.series = s.series;
        nc.number = s.lastNumber;
        nc.issueDate = now.toLocalDate();
        nc.issueTime = now.toLocalTime().truncatedTo(ChronoUnit.SECONDS);
        nc.patientId = orig.patientId;
        nc.customerDocType = orig.customerDocType;
        nc.customerDocNumber = orig.customerDocNumber;
        nc.customerName = orig.customerName;
        nc.customerAddress = orig.customerAddress;
        nc.currency = orig.currency;
        nc.taxableAmount = orig.taxableAmount;
        nc.igvAmount = orig.igvAmount;
        nc.totalAmount = orig.totalAmount;
        nc.status = "GENERADO";
        nc.createdAt = now;
        nc.createdBy = CurrentUser.usernameOrSystem();
        nc.refInvoiceId = orig.id;
        nc.noteCode = code;
        nc.noteReason = reason;
        nc.refFullNumber = orig.getFullNumber();
        nc.refDocType = orig.docType;
        invoices.save(nc);
        List<InvoiceItem> saved = new ArrayList<>();
        for (InvoiceItem o : invoiceItems.findByInvoiceIdOrderByLineNumberAsc(orig.id)) {
            InvoiceItem it = new InvoiceItem();
            it.invoiceId = nc.id;
            it.lineNumber = o.lineNumber;
            it.description = o.description;
            it.quantity = o.quantity;
            it.unitPrice = o.unitPrice;
            it.unitValue = o.unitValue;
            it.lineValue = o.lineValue;
            it.lineIgv = o.lineIgv;
            it.lineTotal = o.lineTotal;
            saved.add(invoiceItems.save(it));
        }
        XmlSigner.Signed signed = signer.sign(UblBuilder.build(company, nc, saved));
        nc.xmlContent = signed.xml();
        nc.hash = signed.digestValue();
        invoices.save(nc);
        audit.register("COMPROBANTE", nc.id, "NOTA_CREDITO", nc.getFullNumber() + " anula " + orig.getFullNumber() + " · " + reason);
        return send(nc.id);
    }

    /** La nota de crédito fue aceptada: el comprobante original queda anulado y libera procedimientos y cobros. */
    private void annulReferenced(Invoice nc) {
        invoices.findById(nc.refInvoiceId).ifPresent(orig -> {
            if ("ANULADO".equals(orig.status)) return;
            orig.status = "ANULADO";
            orig.sunatDescription = "Anulado con la nota de crédito " + nc.getFullNumber();
            invoices.save(orig);
            for (InvoiceItem it : invoiceItems.findByInvoiceIdOrderByLineNumberAsc(orig.id)) {
                if (it.treatmentItemId != null) {
                    treatmentItems.findById(it.treatmentItemId).ifPresent(ti -> { ti.invoiceId = null; treatmentItems.save(ti); });
                }
            }
            jdbc.update("update payments set invoice_id = null where invoice_id = ?", orig.id);
            audit.register("COMPROBANTE", orig.id, "ANULADO", orig.getFullNumber() + " anulado por " + nc.getFullNumber());
        });
    }

    // ------------------------------------------------------------------ HU-12 consulta

    public Invoice get(Long id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> ApiException.notFound("El comprobante"));
        if (inv.refInvoiceId != null && inv.refFullNumber == null) {
            invoices.findById(inv.refInvoiceId).ifPresent(o -> { inv.refFullNumber = o.getFullNumber(); inv.refDocType = o.docType; });
        }
        return inv;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return detailInternal(id);
    }

    private Map<String, Object> detailInternal(Long id) {
        Invoice inv = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoice", inv);
        m.put(ITEMS, invoiceItems.findByInvoiceIdOrderByLineNumberAsc(id));
        m.put("submissions", submissions.findByInvoiceIdOrderByAttemptAtDesc(id));
        m.put("amountInWords", AmountInWords.of(inv.totalAmount));
        m.put("company", company);
        if (inv.refInvoiceId != null) {
            invoices.findById(inv.refInvoiceId).ifPresent(o -> { inv.refFullNumber = o.getFullNumber(); inv.refDocType = o.docType;
                m.put("reference", Map.of("id", o.id, "fullNumber", o.getFullNumber(), "docType", o.docType)); });
            m.put("noteLabel", NOTE_CODES.getOrDefault(inv.noteCode, inv.noteCode));
        }
        m.put("creditNotes", jdbc.queryForList("""
                select id, concat(series, '-', lpad(number, 8, '0')) as fullNumber, status, note_reason as reason
                  from invoices where ref_invoice_id = ? order by id""", id));
        if (inv.patientId != null) {
            patients.findById(inv.patientId).ifPresent(p -> m.put("patient", Map.of("id", p.id, "fullName", p.getFullName())));
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(String q, String status, String docType, LocalDate from, LocalDate to, int page) {
        String query = q == null ? "" : q.trim().toUpperCase();
        Specification<Invoice> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!query.isEmpty()) {
                if (query.matches("^[BF]\\d{3}-?\\d+$")) {
                    String ser = query.substring(0, 4);
                    int num = Integer.parseInt(query.substring(4).replace("-", ""));
                    ps.add(cb.and(cb.equal(root.get("series"), ser), cb.equal(root.get("number"), num)));
                } else {
                    String like = "%" + query + "%";
                    ps.add(cb.or(cb.like(cb.upper(root.<String>get("customerName")), like),
                                 cb.like(root.<String>get(CUSTOMERDOCNUMBER), like)));
                }
            }
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (docType != null && !docType.isBlank()) ps.add(cb.equal(root.get("docType"), docType));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("issueDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.<LocalDate>get("issueDate"), to));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var result = invoices.findAll(spec, PageRequest.of(Math.max(page, 0), 25, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", result.getContent());
        m.put("total", result.getTotalElements());
        m.put("page", result.getNumber());
        m.put("pages", result.getTotalPages());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Invoice> byPatient(Long patientId) {
        return invoices.findByPatientIdOrderByCreatedAtDesc(patientId);
    }
}
