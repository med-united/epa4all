package de.servicehealth.epa4all.soap;

import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import jakarta.xml.ws.WebServiceException;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class XDSCallTest {

    @Test
    public void xdsCallWorks() throws Exception {
        QName serviceName = new QName("urn:ihe:iti:xds-b:2007", "XDSDocumentService");
        File wsdlFile = new File("src/main/resources/schema/XDSDocumentService.wsdl");
        URL wsdlURL = new URL("file://" + wsdlFile.getAbsolutePath());
        XDSDocumentService xdsService = new XDSDocumentService(wsdlURL, serviceName);

        RetrieveDocumentSetRequestType body = new RetrieveDocumentSetRequestType();
        RetrieveDocumentSetRequestType.DocumentRequest documentRequest = new RetrieveDocumentSetRequestType.DocumentRequest();
        documentRequest.setDocumentUniqueId("test");
        documentRequest.setHomeCommunityId("commId");
        documentRequest.setRepositoryUniqueId("repoId");
        body.getDocumentRequest().add(documentRequest);
        IDocumentManagementPortType documentManagement = xdsService.getIDocumentManagement();
        assertThrows(WebServiceException.class, () -> documentManagement.documentRepositoryRetrieveDocumentSet(body));
    }
}
