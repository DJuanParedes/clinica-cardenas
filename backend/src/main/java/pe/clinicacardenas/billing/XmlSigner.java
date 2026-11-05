package pe.clinicacardenas.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Firma digital del comprobante como la exige SUNAT: XMLDSig enveloped, RSA-SHA256,
 * con Id "SignatureSP" dentro de ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent.
 * El certificado se configura con app.sunat.certificado (PKCS12 / .pfx). Por defecto usa un
 * certificado de demostración autofirmado: para producción reemplázalo por el certificado
 * digital tributario de la clínica.
 */
@Component
public class XmlSigner {

    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";

    private final PrivateKey key;
    private final X509Certificate cert;

    public XmlSigner(@Value("${app.sunat.certificado:classpath:sunat/certificado-demo.p12}") String location,
                     @Value("${app.sunat.certificado-clave:clinica-demo}") String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new DefaultResourceLoader().getResource(location).getInputStream()) {
            ks.load(in, password.toCharArray());
        }
        String alias = Collections.list(ks.aliases()).stream().filter(a -> {
            try { return ks.isKeyEntry(a); } catch (Exception e) { return false; }
        }).findFirst().orElseThrow(() -> new IllegalStateException("El certificado no tiene clave privada"));
        this.key = (PrivateKey) ks.getKey(alias, password.toCharArray());
        this.cert = (X509Certificate) ks.getCertificate(alias);
    }

    public record Signed(String xml, String digestValue) {}

    public Signed sign(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            Node target = doc.getElementsByTagNameNS(EXT, "ExtensionContent").item(0);
            if (target == null) throw new IllegalStateException("El XML no tiene ext:ExtensionContent");

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference("",
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                    List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509 = kif.newX509Data(List.of(cert.getSubjectX500Principal().getName(), cert));
            KeyInfo ki = kif.newKeyInfo(List.of(x509));

            DOMSignContext ctx = new DOMSignContext(key, target);
            ctx.setDefaultNamespacePrefix("ds");
            XMLSignature signature = fac.newXMLSignature(si, ki, null, "SignatureSP", null);
            signature.sign(ctx);

            String digest = ((Element) doc.getElementsByTagNameNS(XMLSignature.XMLNS, "DigestValue").item(0)).getTextContent();

            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return new Signed(out.toString(), digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el comprobante: " + e.getMessage(), e);
        }
    }

    public String certificateSubject() {
        return cert.getSubjectX500Principal().getName();
    }
}
