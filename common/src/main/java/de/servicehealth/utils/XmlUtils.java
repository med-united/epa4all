package de.servicehealth.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import static de.servicehealth.utils.ServerUtils.decompress;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.transform.OutputKeys.ENCODING;
import static javax.xml.transform.OutputKeys.INDENT;

public class XmlUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlUtils.class.getName());

    private static TransformerFactory transformerFactory;
    private static DocumentBuilder documentBuilder;

    static {
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            documentFactory.setFeature(FEATURE_SECURE_PROCESSING, true);
            documentFactory.setNamespaceAware(true);
            documentBuilder = documentFactory.newDocumentBuilder();
            transformerFactory = TransformerFactory.newInstance();
        } catch (Exception e) {
            log.error("Could create parser", e);
        }
    }

    public static String print(Node node) {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(INDENT, "yes");
            transformer.setOutputProperty(ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Error while printing XML document", e);
            return e.getMessage();
        }
    }

    public static synchronized Document createDocument() {
        return documentBuilder.newDocument();
    }

    public static synchronized Document createDocument(InputStream inputStream) throws IOException, SAXException {
        return documentBuilder.parse(inputStream);
    }

    public static synchronized Document createDocument(byte[] bytes) throws IOException, SAXException {
        return documentBuilder.parse(new ByteArrayInputStream(decompress(bytes)));
    }
}
