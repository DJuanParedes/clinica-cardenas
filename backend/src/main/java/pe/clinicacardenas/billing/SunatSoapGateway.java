package pe.clinicacardenas.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Cliente del servicio SOAP de facturación electrónica de SUNAT (billService).
 * Empaqueta el XML firmado en un ZIP RUC-TIPO-SERIE-NUMERO.zip, lo envía con sendBill usando
 * WS-Security (usuario SOL secundario) y lee la Constancia de Recepción (CDR) que devuelve.
 *
 * Interpretación de la respuesta, según el catálogo de errores de SUNAT:
 *  - CDR con código 0 → ACEPTADO; con notas 4000+ → OBSERVADO; código 2000–3999 → RECHAZADO.
 *  - SOAP Fault 0100–1999 → ERROR (excepción: el comprobante no quedó registrado y se puede reenviar).
 *  - 1033 (ya registrado) → se recupera el CDR con getStatusCdr.
 *  - Sin conexión o HTTP 5xx → ERROR, reintentable.
 */
@Component
@ConditionalOnProperty(name = "app.sunat.mode", havingValue = "soap", matchIfMissing = true)
public class SunatSoapGateway implements SunatGateway {

    private static final Logger log = LoggerFactory.getLogger(SunatSoapGateway.class);
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final Pattern FAULT_CODE = Pattern.compile("<faultcode>[^<]*?(\\d{4})</faultcode>");
    private static final Pattern FAULT_STRING = Pattern.compile("<faultstring>([\\s\\S]*?)</faultstring>");

    private final String ruc;
    private final String url;
    private final String consultaUrl;
    private final String usuario;
    private final String clave;
    private final String environment;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public SunatSoapGateway(@Value("${app.company.ruc}") String ruc,
                            @Value("${app.sunat.url}") String url,
                            @Value("${app.sunat.consulta-url}") String consultaUrl,
                            @Value("${app.sunat.usuario}") String usuario,
                            @Value("${app.sunat.clave}") String clave,
                            @Value("${app.sunat.ambiente}") String environment,
                            ObjectMapper json) {
        this.ruc = ruc;
        this.url = url;
        this.consultaUrl = consultaUrl;
        this.usuario = usuario;
        this.clave = clave;
        this.environment = environment;
        this.json = json;
        log.info("SUNAT: envío por SOAP a {} (usuario {}{})", url, ruc, usuario);
    }

    // ------------------------------------------------------------------ sendBill

    @Override
    public Response send(Invoice inv, String xml) {
        String name = fileName(inv);
        String zip = Base64.getEncoder().encodeToString(zip(name + ".xml", xml.getBytes(StandardCharsets.UTF_8)));
        String body = "<ser:sendBill><fileName>" + name + ".zip</fileName><contentFile>" + zip + "</contentFile></ser:sendBill>";
        HttpResponse<String> res;
        try {
            res = post(body, Duration.ofSeconds(15));
        } catch (Exception e) {
            return error(null, "SUNAT no respondió (" + reason(e) + "). El comprobante queda pendiente; reintenta el envío.");
        }
        String text = res.body() == null ? "" : res.body();
        if (text.contains("applicationResponse>")) {
            return fromCdr(between(text, "applicationResponse>", "</"));
        }
        Matcher fc = FAULT_CODE.matcher(text);
        if (fc.find()) {
            String code = fc.group(1);
            Matcher fs = FAULT_STRING.matcher(text);
            String msg = fs.find() ? unescape(fs.group(1)) : "Error " + code;
            if ("1033".equals(code)) {
                // Ya estaba registrado (p. ej. se cortó la conexión en el envío anterior): recuperamos su CDR.
                Optional<Response> st = status(inv);
                if (st.isPresent()) return st.get();
            }
            int n = Integer.parseInt(code);
            return new Response(n >= 2000 && n < 4000 ? "RECHAZADO" : "ERROR", code, msg, null, environment);
        }
        return error(null, "SUNAT respondió HTTP " + res.statusCode() + " sin constancia. Reintenta el envío en unos minutos.");
    }

    // ------------------------------------------------------------------ getStatusCdr

    @Override
    public Optional<Response> status(Invoice inv) {
        String body = "<ser:getStatusCdr><rucComprobante>" + ruc + "</rucComprobante><tipoComprobante>" + inv.docType
                + "</tipoComprobante><serieComprobante>" + inv.series + "</serieComprobante><numeroComprobante>" + inv.number
                + "</numeroComprobante></ser:getStatusCdr>";
        HttpResponse<String> res;
        try {
            res = post(body, Duration.ofSeconds(10));
        } catch (Exception e) {
            throw new IllegalStateException("SUNAT no respondió (" + reason(e) + ")");
        }
        String text = res.body() == null ? "" : res.body();
        if (res.statusCode() >= 500 && !text.contains("<faultcode>")) {
            throw new IllegalStateException("SUNAT respondió HTTP " + res.statusCode());
        }
        Matcher fc = FAULT_CODE.matcher(text);
        if (fc.find()) {
            Matcher fs = FAULT_STRING.matcher(text);
            throw new IllegalStateException(fs.find() ? unescape(fs.group(1)) : "Error " + fc.group(1));
        }
        if (!text.contains("<content>")) return Optional.empty();
        return Optional.of(fromCdr(between(text, "<content>", "</content>")));
    }

    // ------------------------------------------------------------------ consulta RUC

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> lookupRuc(String number) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(consultaUrl + "/" + number))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 404 || res.statusCode() == 422) return Optional.empty();
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            return Optional.of(json.readValue(res.body(), Map.class));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("La consulta RUC de SUNAT no respondió (" + reason(e) + ")");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private HttpResponse<String> post(String operation, Duration timeout) throws Exception {
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>" + ruc + usuario + "</wsse:Username>"
                + "<wsse:Password>" + escape(clave) + "</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body>" + operation + "</soapenv:Body></soapenv:Envelope>";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:" + (operation.contains("getStatusCdr") ? "getStatusCdr" : "sendBill"))
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Response fromCdr(String base64Zip) {
        try {
            byte[] cdrBytes = unzipFirstXml(Base64.getDecoder().decode(base64Zip.trim()));
            String cdr = new String(cdrBytes, StandardCharsets.UTF_8);
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(cdrBytes));
            String code = text(doc.getElementsByTagNameNS(CBC, "ResponseCode"));
            String desc = text(doc.getElementsByTagNameNS(CBC, "Description"));
            List<String> notes = new ArrayList<>();
            NodeList ns = doc.getElementsByTagNameNS(CBC, "Note");
            for (int i = 0; i < ns.getLength(); i++) notes.add(ns.item(i).getTextContent());
            int n = code == null ? -1 : Integer.parseInt(code.trim());
            String status;
            if (n == 0) status = notes.isEmpty() ? "ACEPTADO" : "OBSERVADO";
            else if (n >= 4000) status = "OBSERVADO";
            else if (n >= 2000) status = "RECHAZADO";
            else status = "ERROR";
            if (!notes.isEmpty()) desc = desc + " · Observaciones: " + String.join("; ", notes);
            return new Response(status, code, desc, cdr, environment);
        } catch (Exception e) {
            return error(null, "No se pudo leer la constancia (CDR) de SUNAT: " + e.getMessage());
        }
    }

    private Response error(String code, String msg) {
        log.warn("SUNAT: {}", msg);
        return new Response("ERROR", code, msg, null, environment);
    }

    String fileName(Invoice inv) {
        return ruc + "-" + inv.docType + "-" + inv.series + "-" + inv.number;
    }

    static byte[] zip(String entry, byte[] data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry(entry));
            z.write(data);
            z.closeEntry();
            z.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] unzipFirstXml(byte[] zip) throws Exception {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().toLowerCase().endsWith(".xml")) return z.readAllBytes();
            }
        }
        throw new IllegalStateException("El ZIP no contiene XML");
    }

    private static String text(NodeList nl) {
        Node n = nl.getLength() == 0 ? null : nl.item(0);
        return n == null ? null : n.getTextContent();
    }

    private static String between(String s, String start, String end) {
        int i = s.indexOf(start) + start.length();
        return s.substring(i, s.indexOf(end, i));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&").trim();
    }

    private static String reason(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "tiempo de espera agotado";
        if (e instanceof java.net.ConnectException) return "sin conexión con el servicio";
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
