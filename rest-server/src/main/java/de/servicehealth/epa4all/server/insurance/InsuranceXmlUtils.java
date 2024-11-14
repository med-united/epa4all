package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class InsuranceXmlUtils {

    private static final Logger log = Logger.getLogger(InsuranceXmlUtils.class.getName());

    private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private static DocumentBuilder documentBuilder;

    static {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.log(Level.SEVERE, "Could create parser", e);
        }
    }

    private static final JAXBContext jaxbContext = createJaxbContext();

    private static JAXBContext createJaxbContext() {
        try {
            return JAXBContext.newInstance(
                UCPersoenlicheVersichertendatenXML.class,
                UCAllgemeineVersicherungsdatenXML.class,
                UCGeschuetzteVersichertendatenXML.class
            );
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Could not init jaxb context", e);
            return null;
        }
    }

    public static Document createDocument(byte[] bytes) throws IOException, SAXException {
        String decoded = new String(new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes());
        return documentBuilder.parse(new ByteArrayInputStream(decoded.getBytes()));
    }

    @SuppressWarnings("unchecked")
    public static <T> T createUCDocument(byte[] bytes) throws Exception {
        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            assert jaxbContext != null;
            return (T) jaxbContext.createUnmarshaller().unmarshal(is);
        }
    }

    private InsuranceXmlUtils() {
    }
}
