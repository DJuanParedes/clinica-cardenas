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
            if (!"6".equals(cdt)) e.put("customerDocType", "La factura se emite solo a un RUC.");
            else if (!RucValidator.isValid(doc)) e.put("customerDocNumber", "El RUC no es válido (11 dígitos y dígito verificador).");
            if (r.customerAddress() == null || r.customerAddress().isBlank()) e.put("customerAddress", "Ingresa la dirección fiscal del cliente.");
        } else {
            switch (cdt) {
                case "1" -> { if (!doc.matches("^\\d{8}$")) e.put("customerDocNumber", "El DNI debe tener 8 dígitos."); }
                case "4" -> { if (!doc.matches("^[A-Za-z0-9]{9,12}$")) e.put("customerDocNumber", "Carné de extranjería inválido."); }
                case "7" -> { if (!doc.matches("^[A-Za-z0-9]{6,12}$")) e.put("customerDocNumber", "Pasaporte inválido."); }
                case "6" -> { if (!RucValidator.isValid(doc)) e.put("customerDocNumber", "El RUC no es válido."); }
                case "0" -> {
                    if (total != null && total.compareTo(boletaLimit) > 0) {
                        e.put("customerDocType", "Las boletas mayores a S/ " + boletaLimit.toPlainString() + " requieren identificar al cliente.");
                    }
                }
                default -> e.put("customerDocType", "Selecciona el tipo de documento del cliente.");
            }
        }
        if (!"0".equals(cdt) && (r.customerName() == null || r.customerName().trim().length() < 3)) {
            e.put("customerName", "Ingresa el nombre o razón social del cliente.");
        }
        if (r.items() == null || r.items().isEmpty()) {
            e.put("items", "Agrega al menos un concepto.");
        } else {
            for (LineRequest l : r.items()) {
                if (l.description() == null || l.description().isBlank()) { e.put("items", "Cada concepto necesita una descripción."); break; }
                if (l.quantity() == null || l.quantity().signum() <= 0) { e.put("items", "La cantidad debe ser mayor a cero."); break; }
                if (l.unitPrice() == null || l.unitPrice().signum() <= 0) { e.put("items", "El precio debe ser mayor a cero."); break; }
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
            }
            saved.add(invoiceItems.save(it));
        }
        // XML UBL 2.1 firmado digitalmente; el DigestValue de la firma es el "código hash" del comprobante.
        XmlSigner.Signed signed = signer.sign(UblBuilder.build(company, inv, saved));
        inv.xmlContent = signed.xml();
        inv.hash = signed.digestValue();
        invoices.save(inv);
        audit.record("COMPROBANTE", inv.id, "GENERAR", inv.getFullNumber() + " · S/ " + inv.totalAmount.toPlainString());
        return detail(inv.id);
    }

    // ------------------------------------------------------------------ HU-11 envío a SUNAT

    @Transactional
    public Map<String, Object> send(Long id) {
        Invoice inv = get(id);
        if ("ACEPTADO".equals(inv.status) || "OBSERVADO".equals(inv.status)) {
            throw ApiException.conflict("YA_ENVIADO", "El comprobante ya fue aceptado por SUNAT.");
        }
        if ("RECHAZADO".equals(inv.status)) {
            throw ApiException.conflict("RECHAZADO", "El comprobante fue rechazado. Emite uno nuevo con los datos corregidos.");
        }
        long t0 = System.currentTimeMillis();
        SunatGateway.Response res;
        try {
            res = gateway.send(inv, inv.xmlContent);
        } catch (Exception e) {
            res = new SunatGateway.Response("ERROR", null, "Sin respuesta del servicio: " + e.getMessage(), null, "SUNAT");
        }
        apply(inv, res, (int) (System.currentTimeMillis() - t0), "ENVIO_SUNAT");
        return detail(inv.id);
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
            m = detail(inv.id);
            m.put("statusMessage", before.equals(inv.status)
                    ? "SUNAT confirma el estado " + inv.status + "."
                    : "SUNAT tiene registrado el comprobante como " + inv.status + ". Se actualizó el estado y se guardó la constancia.");
        } else {
            m = detail(inv.id);
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
        if ("RECHAZADO".equals(inv.status) && !"RECHAZADO".equals(previous)) {
            // libera los procedimientos para que puedan facturarse en un comprobante corregido
            for (InvoiceItem it : invoiceItems.findByInvoiceIdOrderByLineNumberAsc(inv.id)) {
                if (it.treatmentItemId != null) {
                    treatmentItems.findById(it.treatmentItemId).ifPresent(ti -> { ti.invoiceId = null; treatmentItems.save(ti); });
                }
            }
        }
        audit.record("COMPROBANTE", inv.id, action, inv.getFullNumber() + " → " + res.status()
                + (res.code() == null ? "" : " (" + res.code() + ")"));
    }

    // ------------------------------------------------------------------ HU-12 consulta

    public Invoice get(Long id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("El comprobante"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Invoice inv = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoice", inv);
        m.put("items", invoiceItems.findByInvoiceIdOrderByLineNumberAsc(id));
        m.put("submissions", submissions.findByInvoiceIdOrderByAttemptAtDesc(id));
        m.put("amountInWords", AmountInWords.of(inv.totalAmount));
        m.put("company", company);
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
                                 cb.like(root.<String>get("customerDocNumber"), like)));
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
