package pe.clinicacardenas.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** v2.19: la nota de crédito se arma como CreditNote UBL 2.1 con referencia al comprobante que anula. */
class NotaCreditoUblTest {

    @Test
    void notaDeCreditoReferenciaLaBoletaYNoLlevaTipoDeFactura() {
        Invoice nc = new Invoice();
        nc.docType = "07";
        nc.series = "BC01";
        nc.number = 1;
        nc.issueDate = LocalDate.of(2026, 11, 28);
        nc.issueTime = LocalTime.of(10, 0);
        nc.customerDocType = "1";
        nc.customerDocNumber = "45872196";
        nc.customerName = "MARIA QUISPE";
        nc.taxableAmount = new BigDecimal("84.75");
        nc.igvAmount = new BigDecimal("15.25");
        nc.totalAmount = new BigDecimal("100.00");
        nc.refFullNumber = "B001-00000012";
        nc.refDocType = "03";
        nc.noteCode = "01";
        nc.noteReason = "Se emitió a otra persona";
        InvoiceItem it = new InvoiceItem();
        it.lineNumber = 1;
        it.description = "Profilaxis";
        it.quantity = BigDecimal.ONE;
        it.unitPrice = new BigDecimal("100.00");
        it.unitValue = new BigDecimal("84.75");
        it.lineValue = new BigDecimal("84.75");
        it.lineIgv = new BigDecimal("15.25");
        it.lineTotal = new BigDecimal("100.00");

        String xml = UblBuilder.build(new UblBuilder.Company("10102984841", "CLINICA", "CLINICA", "LIMA", "150101"), nc, List.of(it));

        assertTrue(xml.contains("<CreditNote xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2\""));
        assertTrue(xml.contains("<cbc:ID>BC01-1</cbc:ID>"));
        assertTrue(xml.contains("<cbc:ReferenceID>B001-12</cbc:ReferenceID><cbc:ResponseCode>01</cbc:ResponseCode>"));
        assertTrue(xml.contains("<cbc:DocumentTypeCode>03</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains("<cac:CreditNoteLine>") && xml.contains("<cbc:CreditedQuantity"));
        assertFalse(xml.contains("InvoiceTypeCode"));
        assertFalse(xml.contains("PaymentTerms"));
        assertTrue(xml.trim().endsWith("</CreditNote>"));
    }
}
