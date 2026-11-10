package pe.clinicacardenas;

import org.junit.jupiter.api.Test;
import pe.clinicacardenas.agenda.SlotCalculator;
import pe.clinicacardenas.auth.PasswordPolicy;
import pe.clinicacardenas.billing.AmountInWords;
import pe.clinicacardenas.billing.InvoiceCalculator;
import pe.clinicacardenas.billing.RucValidator;
import pe.clinicacardenas.billing.XmlSigner;
import pe.clinicacardenas.commission.CommissionRule;
import pe.clinicacardenas.notification.NotificationService;
import pe.clinicacardenas.patient.Patient;
import pe.clinicacardenas.report.ReportService;
import pe.clinicacardenas.clinical.OdontogramRules;
import pe.clinicacardenas.patient.PatientValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de las reglas del Sprint 1 que no requieren base de datos. */
class ReglasDeNegocioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    private PatientValidator.Input paciente(String tipo, String doc) {
        return new PatientValidator.Input(tipo, doc, "María", "Quispe Huamán", LocalDate.of(1991, 3, 14), "F",
                "987 654 321", "maria@correo.pe", null, null, null, null, null);
    }

    @Test
    void hu01_dniValidoSinErrores() {
        assertTrue(PatientValidator.validate(paciente("DNI", "45872196"), HOY).isEmpty());
    }

    @Test
    void hu01_dniDebeTenerOchoDigitos() {
        assertTrue(PatientValidator.validate(paciente("DNI", "4587219"), HOY).containsKey("docNumber"));
        assertTrue(PatientValidator.validate(paciente("DNI", "4587219A"), HOY).containsKey("docNumber"));
    }

    @Test
    void hu01_fechaFuturaNoPermitida() {
        var in = new PatientValidator.Input("DNI", "45872196", "Ana", "Paz", HOY.plusDays(1), "F", "987654321",
                null, null, null, null, null, null);
        assertTrue(PatientValidator.validate(in, HOY).containsKey("birthDate"));
    }

    @Test
    void hu07_noOfreceHorariosQueSeCruzan() {
        LocalDate d = HOY.plusDays(1);
        var slots = SlotCalculator.compute(d,
                List.of(new SlotCalculator.Block(LocalTime.of(9, 0), LocalTime.of(11, 0))),
                List.of(new SlotCalculator.Busy(d.atTime(9, 30), d.atTime(10, 0))), 30, HOY.atStartOfDay());
        assertEquals(4, slots.size());
        assertTrue(slots.get(0).available());   // 09:00
        assertFalse(slots.get(1).available());  // 09:30 ocupado
        assertTrue(slots.get(2).available());   // 10:00
    }

    @Test
    void hu07_citaDebeEntrarEnElHorario() {
        var blocks = List.of(new SlotCalculator.Block(LocalTime.of(9, 0), LocalTime.of(13, 0)));
        assertTrue(SlotCalculator.fitsInBlocks(blocks, LocalTime.of(12, 0), LocalTime.of(13, 0)));
        assertFalse(SlotCalculator.fitsInBlocks(blocks, LocalTime.of(12, 30), LocalTime.of(13, 30)));
    }

    @Test
    void hu25_politicaDeContrasena() {
        assertTrue(PasswordPolicy.isValid("Clinica2026"));
        assertFalse(PasswordPolicy.isValid("corta1"));
        assertFalse(PasswordPolicy.isValid("sinnumeros"));
    }

    @Test
    void hu05_odontogramaNotacionFdi() {
        assertTrue(OdontogramRules.isValidTooth(11));
        assertTrue(OdontogramRules.isValidTooth(85));
        assertFalse(OdontogramRules.isValidTooth(19));
        assertNull(OdontogramRules.check(36, "O", "CARIES"));
        assertNotNull(OdontogramRules.check(36, "X", "CARIES"));
    }

    @Test
    void hu10_rucConDigitoVerificador() {
        assertTrue(RucValidator.isValid("20131312955"));
        assertFalse(RucValidator.isValid("20131312954"));
        assertFalse(RucValidator.isValid("3013131295"));
    }

    @Test
    void hu10_igvIncluidoEnElPrecio() {
        var t = InvoiceCalculator.compute(List.of(
                new InvoiceCalculator.LineIn("Profilaxis", BigDecimal.ONE, new BigDecimal("120.00")),
                new InvoiceCalculator.LineIn("Rx", new BigDecimal("2"), new BigDecimal("35.00"))), new BigDecimal("0.18"));
        assertEquals(new BigDecimal("190.00"), t.total());
        assertEquals(0, t.taxable().add(t.igv()).compareTo(t.total()));
        assertEquals(new BigDecimal("161.01"), t.taxable());
    }

    @Test
    void hu10_importeEnLetras() {
        assertEquals("SON: CIENTO VEINTE CON 00/100 SOLES", AmountInWords.of(new BigDecimal("120")));
        assertEquals("SON: VEINTIUN MIL CON 50/100 SOLES", AmountInWords.of(new BigDecimal("21000.50")));
    }

    @Test
    void hu11_rucDeLaClinicaYFirmaDigitalVerificable() throws Exception {
        assertTrue(RucValidator.isValid("10102984841"));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">"
                + "<ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions><Total>150.00</Total></Invoice>";
        XmlSigner signer = new XmlSigner("classpath:sunat/certificado-demo.p12", "clinica-demo");
        XmlSigner.Signed s = signer.sign(xml);
        assertTrue(s.xml().contains("Id=\"SignatureSP\""));
        assertFalse(s.digestValue().isBlank());

        var f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        var doc = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(s.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var node = doc.getElementsByTagNameNS(javax.xml.crypto.dsig.XMLSignature.XMLNS, "Signature").item(0);
        var ctx = new javax.xml.crypto.dsig.dom.DOMValidateContext(new javax.xml.crypto.KeySelector() {
            public javax.xml.crypto.KeySelectorResult select(javax.xml.crypto.dsig.keyinfo.KeyInfo ki, Purpose p,
                    javax.xml.crypto.AlgorithmMethod m, javax.xml.crypto.XMLCryptoContext c) {
                for (Object o : ki.getContent()) {
                    if (o instanceof javax.xml.crypto.dsig.keyinfo.X509Data x) {
                        for (Object e : x.getContent()) {
                            if (e instanceof java.security.cert.X509Certificate cert) return () -> cert.getPublicKey();
                        }
                    }
                }
                throw new IllegalStateException("sin certificado");
            }
        }, node);
        var sig = javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx);
        assertTrue(sig.validate(ctx), "la firma debe validar con el certificado incluido");

        // Alterar el monto invalida la firma
        var tampered = s.xml().replace("150.00", "15.00");
        var doc2 = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var ctx2 = new javax.xml.crypto.dsig.dom.DOMValidateContext(ctx.getKeySelector(),
                doc2.getElementsByTagNameNS(javax.xml.crypto.dsig.XMLSignature.XMLNS, "Signature").item(0));
        assertFalse(javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx2).validate(ctx2));
    }

    // ------------------------------------------------------------------ versión completa

    @Test
    void hu23_recordatorio24HorasAntesO2HorasSiLaCitaEsPronto() {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 5, 9, 0);
        assertEquals(java.time.LocalDateTime.of(2026, 11, 7, 10, 0), NotificationService.reminderTime(java.time.LocalDateTime.of(2026, 11, 8, 10, 0), now));
        assertEquals(java.time.LocalDateTime.of(2026, 11, 5, 17, 0), NotificationService.reminderTime(java.time.LocalDateTime.of(2026, 11, 5, 19, 0), now));
        assertNull(NotificationService.reminderTime(java.time.LocalDateTime.of(2026, 11, 5, 10, 30), now));
    }

    @Test
    void hu23_canalSegunPreferenciaDelPaciente() {
        Patient p = new Patient();
        p.phone = "987654321";
        p.email = "paciente@correo.pe";
        p.notifyChannel = "AMBOS";
        assertEquals(2, NotificationService.targets(p).size());
        p.notifyChannel = "NINGUNO";
        assertTrue(NotificationService.targets(p).isEmpty());
        p.notifyChannel = "EMAIL";
        p.email = null;
        assertEquals("WHATSAPP", NotificationService.targets(p).get(0).channel(), "sin correo se usa WhatsApp");
    }

    @Test
    void hu16_vigenciaDeLaRegla() {
        CommissionRule r = new CommissionRule();
        r.active = true;
        r.calcType = "PORCENTAJE";
        r.value = new BigDecimal("40");
        r.base = "REALIZADO";
        r.validFrom = LocalDate.of(2026, 1, 1);
        r.validTo = LocalDate.of(2026, 6, 30);
        assertTrue(r.appliesOn(LocalDate.of(2026, 3, 15)));
        assertFalse(r.appliesOn(LocalDate.of(2026, 7, 1)));
        assertEquals("40 % del precio · todos los procedimientos", r.getLabel());
        r.active = false;
        assertFalse(r.appliesOn(LocalDate.of(2026, 3, 15)));
    }

    @Test
    void hu19_agrupacionAutomaticaDelPeriodo() {
        assertEquals("day", ReportService.grouping(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null));
        assertEquals("week", ReportService.grouping(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 5), null));
        assertEquals("month", ReportService.grouping(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 5), null));
        assertEquals(LocalDate.of(2026, 11, 2), ReportService.bucket(LocalDate.of(2026, 11, 5), "week"));
    }
}
