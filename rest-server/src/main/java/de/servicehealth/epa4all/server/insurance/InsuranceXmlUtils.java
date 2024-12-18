package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import jakarta.xml.bind.JAXBContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.ServerUtils.decompress;

public class InsuranceXmlUtils {

    private static final Logger log = Logger.getLogger(InsuranceXmlUtils.class.getName());

    private static DocumentBuilder documentBuilder;
    private static JAXBContext jaxbContext;

    static {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilder = factory.newDocumentBuilder();
            jaxbContext = createJaxbContext();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could create parser", e);
        }
    }

    private static JAXBContext createJaxbContext() throws Exception {
        return JAXBContext.newInstance(
            UCPersoenlicheVersichertendatenXML.class,
            UCAllgemeineVersicherungsdatenXML.class,
            UCGeschuetzteVersichertendatenXML.class
        );
    }

    public static Document createDocument(byte[] bytes) throws IOException, SAXException {
        return documentBuilder.parse(new ByteArrayInputStream(decompress(bytes)));
    }

    @SuppressWarnings("unchecked")
    public static <T> T createUCEntity(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return (T) jaxbContext.createUnmarshaller().unmarshal(new ByteArrayInputStream(decompress(bytes)));
    }

    private InsuranceXmlUtils() {
    }
}
