package de.servicehealth.epa4all.server.filetracker.upload.soap;

import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

@SuppressWarnings("DuplicatedCode")
public class RawSoapUtils {

    private static final Logger log = LoggerFactory.getLogger(RawSoapUtils.class.getName());

    private static final String ROOT_WRAPPER = "<?xml version=\"1.0\" encoding=\"utf-8\"?><root>%s</root>";

    private static JAXBContext jaxbContext;

    static {
        try {
            jaxbContext = JAXBContext.newInstance(SoapRoot.class);
        } catch (Exception e) {
            log.error("Could create unmarshaller", e);
        }
    }

    public static ProvideAndRegisterDocumentSetRequestType deserialize(String rawSoapRequest) throws Exception {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        StringReader reader = new StringReader(ROOT_WRAPPER.formatted(rawSoapRequest));
        SoapRoot soapRoot = (SoapRoot) unmarshaller.unmarshal(reader);
        return soapRoot.getRequest();
    }

    private RawSoapUtils() {
    }
}
