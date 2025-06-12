package de.servicehealth.epa4all.server.filetracker.upload.soap;

import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.RemoveObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

@SuppressWarnings("DuplicatedCode")
public class RawSoapUtils {

    private static final Logger log = LoggerFactory.getLogger(RawSoapUtils.class.getName());

    private static final String ROOT_WRAPPER = "<?xml version=\"1.0\" encoding=\"utf-8\"?><root>%s</root>";

    private static JAXBContext uploadContext;
    private static JAXBContext deleteContext;
    private static JAXBContext adhocContext;

    private RawSoapUtils() {
    }

    static {
        try {
            uploadContext = JAXBContext.newInstance(UploadRoot.class);
            deleteContext = JAXBContext.newInstance(DeleteRoot.class);
            adhocContext = JAXBContext.newInstance(AdhocResponseRoot.class);
        } catch (Exception e) {
            log.error("Could create unmarshaller", e);
        }
    }

    public static ProvideAndRegisterDocumentSetRequestType deserializeUploadRequest(String rawSoapRequest) throws Exception {
        Unmarshaller unmarshaller = uploadContext.createUnmarshaller();
        StringReader reader = new StringReader(ROOT_WRAPPER.formatted(rawSoapRequest));
        UploadRoot uploadRoot = (UploadRoot) unmarshaller.unmarshal(reader);
        return uploadRoot.getRequest();
    }

    public static RemoveObjectsRequest deserializeDeleteRequest(String rawSoapRequest) throws Exception {
        Unmarshaller unmarshaller = deleteContext.createUnmarshaller();
        StringReader reader = new StringReader(ROOT_WRAPPER.formatted(rawSoapRequest));
        DeleteRoot deleteRoot = (DeleteRoot) unmarshaller.unmarshal(reader);
        return deleteRoot.getRequest();
    }

    public static AdhocQueryResponse deserializeAdhocQueryResponse(String rawSoapRequest) throws Exception {
        Unmarshaller unmarshaller = adhocContext.createUnmarshaller();
        StringReader reader = new StringReader(ROOT_WRAPPER.formatted(rawSoapRequest));
        AdhocResponseRoot adhocResponseRoot = (AdhocResponseRoot) unmarshaller.unmarshal(reader);
        return adhocResponseRoot.getResponse();
    }
}
