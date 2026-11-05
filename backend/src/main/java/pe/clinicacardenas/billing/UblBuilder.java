package pe.clinicacardenas.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/**
 * Genera el XML UBL 2.1 del comprobante (formato SUNAT, versión de personalización 2.0).
 * El nodo de firma queda como bloque de extensión: la firma digital con certificado
 * se incorpora al pasar al entorno beta/producción (spike SP-01).
 */
public final class UblBuilder {

    private UblBuilder() {}

    public record Company(String ruc, String businessName, String tradeName, String address, String ubigeo) {}

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String amt(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public static String build(Company c, Invoice inv, List<InvoiceItem> items) {
        String cur = inv.currency;
        StringBuilder x = new StringBuilder(4096);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"\n")
         .append("         xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n")
         .append("         xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"\n")
         .append("         xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"\n")
         .append("         xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">\n");
        x.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>\n");
        x.append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n");
        x.append("  <cbc:CustomizationID>2.0</cbc:CustomizationID>\n");
        x.append("  <cbc:ID>").append(inv.getFullNumber().replaceFirst("-0+", "-")).append("</cbc:ID>\n");
        x.append("  <cbc:IssueDate>").append(inv.issueDate).append("</cbc:IssueDate>\n");
        x.append("  <cbc:IssueTime>").append(inv.issueTime).append("</cbc:IssueTime>\n");
        x.append("  <cbc:InvoiceTypeCode listID=\"0101\">").append(inv.docType).append("</cbc:InvoiceTypeCode>\n");
        x.append("  <cbc:Note languageLocaleID=\"1000\">").append(esc(AmountInWords.of(inv.totalAmount))).append("</cbc:Note>\n");
        x.append("  <cbc:DocumentCurrencyCode>").append(cur).append("</cbc:DocumentCurrencyCode>\n");

        x.append("  <cac:Signature><cbc:ID>").append(c.ruc()).append("</cbc:ID>")
         .append("<cac:SignatoryParty><cac:PartyIdentification><cbc:ID>").append(c.ruc()).append("</cbc:ID></cac:PartyIdentification>")
         .append("<cac:PartyName><cbc:Name>").append(esc(c.businessName())).append("</cbc:Name></cac:PartyName></cac:SignatoryParty>")
         .append("<cac:DigitalSignatureAttachment><cac:ExternalReference><cbc:URI>#SignatureSP</cbc:URI></cac:ExternalReference>")
         .append("</cac:DigitalSignatureAttachment></cac:Signature>\n");

        x.append("  <cac:AccountingSupplierParty><cac:Party>")
         .append("<cac:PartyIdentification><cbc:ID schemeID=\"6\">").append(c.ruc()).append("</cbc:ID></cac:PartyIdentification>")
         .append("<cac:PartyName><cbc:Name>").append(esc(c.tradeName())).append("</cbc:Name></cac:PartyName>")
         .append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(c.businessName())).append("</cbc:RegistrationName>")
         .append("<cac:RegistrationAddress><cbc:ID>").append(c.ubigeo()).append("</cbc:ID><cbc:AddressTypeCode>0000</cbc:AddressTypeCode>")
         .append("<cac:AddressLine><cbc:Line>").append(esc(c.address())).append("</cbc:Line></cac:AddressLine>")
         .append("<cac:Country><cbc:IdentificationCode>PE</cbc:IdentificationCode></cac:Country></cac:RegistrationAddress>")
         .append("</cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>\n");

        x.append("  <cac:AccountingCustomerParty><cac:Party>")
         .append("<cac:PartyIdentification><cbc:ID schemeID=\"").append(inv.customerDocType).append("\">")
         .append(esc(inv.customerDocNumber)).append("</cbc:ID></cac:PartyIdentification>")
         .append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(inv.customerName)).append("</cbc:RegistrationName>");
        if (inv.customerAddress != null) {
            x.append("<cac:RegistrationAddress><cac:AddressLine><cbc:Line>").append(esc(inv.customerAddress))
             .append("</cbc:Line></cac:AddressLine></cac:RegistrationAddress>");
        }
        x.append("</cac:PartyLegalEntity></cac:Party></cac:AccountingCustomerParty>\n");

        x.append("  <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Contado</cbc:PaymentMeansID></cac:PaymentTerms>\n");

        x.append("  <cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(cur).append("\">").append(amt(inv.igvAmount)).append("</cbc:TaxAmount>")
         .append("<cac:TaxSubtotal><cbc:TaxableAmount currencyID=\"").append(cur).append("\">").append(amt(inv.taxableAmount)).append("</cbc:TaxableAmount>")
         .append("<cbc:TaxAmount currencyID=\"").append(cur).append("\">").append(amt(inv.igvAmount)).append("</cbc:TaxAmount>")
         .append("<cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>")
         .append("</cac:TaxSubtotal></cac:TaxTotal>\n");

        x.append("  <cac:LegalMonetaryTotal>")
         .append("<cbc:LineExtensionAmount currencyID=\"").append(cur).append("\">").append(amt(inv.taxableAmount)).append("</cbc:LineExtensionAmount>")
         .append("<cbc:TaxInclusiveAmount currencyID=\"").append(cur).append("\">").append(amt(inv.totalAmount)).append("</cbc:TaxInclusiveAmount>")
         .append("<cbc:PayableAmount currencyID=\"").append(cur).append("\">").append(amt(inv.totalAmount)).append("</cbc:PayableAmount>")
         .append("</cac:LegalMonetaryTotal>\n");

        for (InvoiceItem it : items) {
            x.append("  <cac:InvoiceLine>")
             .append("<cbc:ID>").append(it.lineNumber).append("</cbc:ID>")
             .append("<cbc:InvoicedQuantity unitCode=\"ZZ\">").append(it.quantity.toPlainString()).append("</cbc:InvoicedQuantity>")
             .append("<cbc:LineExtensionAmount currencyID=\"").append(cur).append("\">").append(amt(it.lineValue)).append("</cbc:LineExtensionAmount>")
             .append("<cac:PricingReference><cac:AlternativeConditionPrice><cbc:PriceAmount currencyID=\"").append(cur).append("\">")
             .append(amt(it.unitPrice)).append("</cbc:PriceAmount><cbc:PriceTypeCode>01</cbc:PriceTypeCode></cac:AlternativeConditionPrice></cac:PricingReference>")
             .append("<cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(cur).append("\">").append(amt(it.lineIgv)).append("</cbc:TaxAmount>")
             .append("<cac:TaxSubtotal><cbc:TaxableAmount currencyID=\"").append(cur).append("\">").append(amt(it.lineValue)).append("</cbc:TaxableAmount>")
             .append("<cbc:TaxAmount currencyID=\"").append(cur).append("\">").append(amt(it.lineIgv)).append("</cbc:TaxAmount>")
             .append("<cac:TaxCategory><cbc:Percent>18.00</cbc:Percent><cbc:TaxExemptionReasonCode>10</cbc:TaxExemptionReasonCode>")
             .append("<cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>")
             .append("</cac:TaxSubtotal></cac:TaxTotal>")
             .append("<cac:Item><cbc:Description>").append(esc(it.description)).append("</cbc:Description></cac:Item>")
             .append("<cac:Price><cbc:PriceAmount currencyID=\"").append(cur).append("\">").append(it.unitValue.toPlainString()).append("</cbc:PriceAmount></cac:Price>")
             .append("</cac:InvoiceLine>\n");
        }
        x.append("</Invoice>\n");
        return x.toString();
    }

    /** Resumen (hash) del XML que se imprime en la representación del comprobante. */
    public static String digest(String xml) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d).substring(0, 28);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
