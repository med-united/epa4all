package de.servicehealth.epa4all.server.vsds;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class PersoenlicheVersichertendateXmlUtils {

    private static final Logger log = Logger.getLogger(PersoenlicheVersichertendateXmlUtils.class.getName());

    static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    static DocumentBuilder documentBuilder;

    static {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.log(Level.SEVERE, "Could create parser", e);
        }
    }

    static final JAXBContext jaxbContext = createJaxbContext();

    static JAXBContext createJaxbContext() {
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

    static UCPersoenlicheVersichertendatenXML getPatient(byte[] persBytes) throws Exception {
        try (InputStream isPersoenlicheVersichertendaten = new GZIPInputStream(new ByteArrayInputStream(persBytes))) {
            return (UCPersoenlicheVersichertendatenXML) jaxbContext
                .createUnmarshaller()
                .unmarshal(isPersoenlicheVersichertendaten);
        }
    }

    private PersoenlicheVersichertendateXmlUtils() {
    }
}
