package de.servicehealth.epa4all.soap;

import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import jakarta.xml.ws.WebServiceException;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;
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


        ProvideAndRegisterDocumentSetRequestType request = new ProvideAndRegisterDocumentSetRequestType();
        SubmitObjectsRequest objectsRequest = new SubmitObjectsRequest();
        SlotListType slotList = new SlotListType();
        SlotType1 slotType = new SlotType1();
        slotType.setName("Slot");
        slotType.setSlotType("SlotType");
        ValueListType valueList = new ValueListType();
        valueList.getValue().add("Value");
        slotType.setValueList(valueList);
        slotList.getSlot().add(slotType);

        objectsRequest.setRequestSlotList(slotList);
        objectsRequest.setId("ID");
        RegistryObjectListType registryObjectList = new RegistryObjectListType();
        // registryObjectList.getIdentifiable().add(new JAXBElement<IdentifiableType>())
        objectsRequest.setRegistryObjectList(registryObjectList);

        request.setSubmitObjectsRequest(objectsRequest);
        // produces URI with undefined scheme
        // documentManagement.documentRepositoryProvideAndRegisterDocumentSetB(request);
    }
}
