package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.idp.IdpClient;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.vsds.VSDService;
import de.servicehealth.model.EntitlementRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;


@Path("xds-document")
public class XDSDocument extends AbstractResource {

    @Inject
    VSDService pharmacyService;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    MultiEpaService multiEpaService;
    
    @Inject
    IdpClient idpClient;

    @GET
    @Path("{konnektor : (\\w+)?}{egkHandle : (/\\w+)?}")
    public String get(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle) {
        try {
            EpaAPI epaAPI = initAndGetEpaAPI(konnektor, egkHandle);
            
            RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
            DocumentRequest documentRequest = new DocumentRequest();
            documentRequest.setDocumentUniqueId(UUID.randomUUID().toString());
            documentRequest.setHomeCommunityId("CommunityId");
            documentRequest.setRepositoryUniqueId("UniqueId");
            retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
            RetrieveDocumentSetResponseType retrieveDocumentSetResponseType = epaAPI.getDocumentManagementPortType().documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
            return retrieveDocumentSetResponseType.getDocumentResponse().stream()
				.map(RetrieveDocumentSetResponseType.DocumentResponse::getDocumentUniqueId)
				.collect(Collectors.joining(", "));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }

    }
}
