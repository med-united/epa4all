package de.servicehealth.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static de.servicehealth.utils.ServerUtils.decompress;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

public class XmlUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlUtils.class.getName());

    private static DocumentBuilder documentBuilder;

    static {
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            documentFactory.setFeature(FEATURE_SECURE_PROCESSING, true);
            documentBuilder = documentFactory.newDocumentBuilder();
        } catch (Exception e) {
            log.error("Could create parser", e);
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
