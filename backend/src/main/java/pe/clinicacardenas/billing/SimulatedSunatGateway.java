package pe.clinicacardenas.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Modo "interno" (app.sunat.mode=interno): simula la respuesta de SUNAT dentro del backend, sin red.
 * El modo predeterminado es "soap" ({@link SunatSoapGateway}) contra la API simulada de SUNAT.
 * Simula la respuesta del entorno beta de SUNAT sin salir a internet: valida el XML como lo haría
 * el servicio (estructura, emisor, totales) y devuelve una Constancia de Recepción (CDR).
 * Códigos usados: 0 aceptado; 2017/2335/3277 rechazos frecuentes del catálogo de errores.
 */
@Component
@ConditionalOnProperty(name = "app.sunat.mode", havingValue = "interno")
public class SimulatedSunatGateway implements SunatGateway {

    private final String companyRuc;

    public SimulatedSunatGateway(@Value("${app.company.ruc}") String companyRuc) {
        this.companyRuc = companyRuc;
    }

    @Override
    public Response send(Invoice inv, String xml) {
        try {
            Thread.sleep(350 + (long) (Math.random() * 450)); // latencia típica del servicio
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String docLabel = "01".equals(inv.docType) ? "Factura" : "Boleta";
        String id = inv.series + "-" + inv.number;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

            NodeList ids = doc.getElementsByTagNameNS(cbc, "ID");
            boolean issuerOk = false;
            for (int i = 0; i < ids.getLength(); i++) {
                if (companyRuc.equals(ids.item(i).getTextContent())) { issuerOk = true; break; }
            }
            if (!issuerOk) {
                return reject(inv, "2017", "El número de documento de identidad del emisor no es válido.");
            }
            if ("01".equals(inv.docType) && (!"6".equals(inv.customerDocType) || !RucValidator.isValid(inv.customerDocNumber))) {
                return reject(inv, "2800", "Para facturas el receptor debe identificarse con un RUC válido.");
            }
            BigDecimal payable = new BigDecimal(doc.getElementsByTagNameNS(cbc, "PayableAmount").item(0).getTextContent());
            if (payable.compareTo(inv.totalAmount) != 0
                    || inv.taxableAmount.add(inv.igvAmount).compareTo(inv.totalAmount) != 0) {
                return reject(inv, "3277", "El importe total no coincide con la suma de valor de venta e IGV.");
            }
        } catch (Exception e) {
            return new Response("ERROR", "0306", "No se pudo leer el XML enviado: " + e.getMessage(), null, "INTERNO");
        }
        String desc = "La " + docLabel + " numero " + id + ", ha sido aceptada";
        return new Response("ACEPTADO", "0", desc, cdr(inv, "0", desc), "INTERNO");
    }

    private Response reject(Invoice inv, String code, String description) {
        return new Response("RECHAZADO", code, description, cdr(inv, code, description), "INTERNO");
    }

    private String cdr(Invoice inv, String code, String description) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<ar:ApplicationResponse xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\"\n"
                + "    xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n"
                + "    xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">\n"
                + "  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>\n"
                + "  <cbc:ID>" + System.currentTimeMillis() + "</cbc:ID>\n"
                + "  <cbc:IssueDate>" + now.toLocalDate() + "</cbc:IssueDate>\n"
                + "  <cbc:IssueTime>" + now.toLocalTime() + "</cbc:IssueTime>\n"
                + "  <cbc:Note>Entorno simulado - sin validez tributaria</cbc:Note>\n"
                + "  <cac:SenderParty><cac:PartyIdentification><cbc:ID>20131312955</cbc:ID></cac:PartyIdentification></cac:SenderParty>\n"
                + "  <cac:ReceiverParty><cac:PartyIdentification><cbc:ID>" + companyRuc + "</cbc:ID></cac:PartyIdentification></cac:ReceiverParty>\n"
                + "  <cac:DocumentResponse>\n"
                + "    <cac:Response><cbc:ReferenceID>" + inv.series + "-" + inv.number + "</cbc:ReferenceID>"
                + "<cbc:ResponseCode>" + code + "</cbc:ResponseCode>"
                + "<cbc:Description>" + description.replace("&", "&amp;").replace("<", "&lt;") + "</cbc:Description></cac:Response>\n"
                + "    <cac:DocumentReference><cbc:ID>" + inv.series + "-" + inv.number + "</cbc:ID></cac:DocumentReference>\n"
                + "  </cac:DocumentResponse>\n"
                + "</ar:ApplicationResponse>\n";
    }
}
