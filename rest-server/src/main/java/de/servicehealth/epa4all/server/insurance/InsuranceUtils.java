package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWT;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import static de.servicehealth.utils.ServerUtils.decompress;
import static jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

public class InsuranceUtils {

    private static final Logger log = LoggerFactory.getLogger(InsuranceUtils.class.getName());

    private static JAXBContext jaxbContext;

    static {
        try {
            jaxbContext = createJaxbContext();
        } catch (Exception e) {
            log.error("Could create parser", e);
        }
    }

    private static JAXBContext createJaxbContext() throws Exception {
        return JAXBContext.newInstance(
            UCPersoenlicheVersichertendatenXML.class,
            UCAllgemeineVersicherungsdatenXML.class,
            UCGeschuetzteVersichertendatenXML.class
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> T createUCEntity(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return (T) jaxbContext.createUnmarshaller().unmarshal(new ByteArrayInputStream(decompress(bytes)));
    }

    public static String print(Object object, boolean formatted) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(JAXB_FORMATTED_OUTPUT, formatted);
            StringWriter sw = new StringWriter();
            marshaller.marshal(object, sw);
            return sw.toString();
        } catch (Exception e) {
            log.error("Error converting object to XML", e);
            return e.getMessage();
        }
    }

    // eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzc2NTQyOTI5LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3NjU0MjkyOSwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.Uqh-NBl3O0jd-xeTR7N0ZLHGkqHo3XBOT-TVh0q8l3BrkBls6dtceZha-1RC2NOXpTkmnjAbAi8m7dlJUcGC-g
    public static Object extractInsurantId(String poppToken) {
        // payload:
        // {
        // "proofMethod": "ehc-practitioner-trustedchannel",
        // "patientProofTime": 1776542929,
        // "actorId": "telematik-id",
        // "patientId": "K210140155",
        // "authorization_details": "details",
        // "iss": "https://popp.example.com",
        // "actorProfessionOid": "1.2.276.0.76.4.50",
        // "version": "1.0.0",
        // "iat": 1776542929,
        // "insurerId": "102171012"
        // }
        // parse poppToken and extract patientId (KVNR)
        JsonObject jwt = JWT.parse(poppToken);
        return jwt.getJsonObject("payload").getString("patientId");
    }

    private InsuranceUtils() {
    }
}
