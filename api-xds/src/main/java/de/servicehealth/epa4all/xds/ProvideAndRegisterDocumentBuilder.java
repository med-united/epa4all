package de.servicehealth.epa4all.xds;

import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;

@SuppressWarnings("UnusedReturnValue")
public class ProvideAndRegisterDocumentBuilder {

    private RegistryObjectListType registryObjectListType;
    private Document document;

    public ProvideAndRegisterDocumentBuilder withRegistryObjectListType(RegistryObjectListType registryObjectListType) {
        this.registryObjectListType = registryObjectListType;
        return this;
    }

    public ProvideAndRegisterDocumentBuilder withDocument(Document document) {
        this.document = document;
        return this;
    }

    public ProvideAndRegisterDocumentSetRequestType build() {
        SubmitObjectsRequest submitObjectsRequest = new SubmitObjectsRequest();
        submitObjectsRequest.setRegistryObjectList(registryObjectListType);
        ProvideAndRegisterDocumentSetRequestType documentSetRequest = new ProvideAndRegisterDocumentSetRequestType();
        documentSetRequest.getDocument().add(document);
        documentSetRequest.setSubmitObjectsRequest(submitObjectsRequest);
        return documentSetRequest;
    }
}
