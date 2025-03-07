package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import jakarta.xml.bind.JAXBContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

import static de.servicehealth.utils.ServerUtils.decompress;

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

    private InsuranceUtils() {
    }
}
