package de.servicehealth.epa4all.server.ws;

import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlRootElement;

import javax.xml.namespace.QName;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

public class XmlEncoder<T> implements Encoder.Text<T> {

    private static final Logger log = Logger.getLogger(XmlEncoder.class.getName());

    private JAXBContext jaxbContext;

    @Override
    public void init(EndpointConfig config) {
        try {
            jaxbContext = JAXBContext.newInstance(RetrieveDocumentSetResponseType.class);
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Failed to initialize JAXBContext", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String encode(T object) {
        try (StringWriter writer = new StringWriter()) {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            if (object.getClass().getAnnotation(XmlRootElement.class) == null) {
                QName qName = new QName("urn:ihe:iti:xds-b:2007", "RetrieveDocumentSetResponseType");
                JAXBElement<T> jaxbElement = new JAXBElement<>(qName, (Class<T>) object.getClass(), object);
                marshaller.marshal(jaxbElement, writer);
            } else {
                marshaller.marshal(object, writer);
            }
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode object to XML", e);
        }
    }
}