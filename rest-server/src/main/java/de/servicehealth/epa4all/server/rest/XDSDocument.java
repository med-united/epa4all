package de.servicehealth.epa4all.server.rest;

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

import java.util.UUID;
import java.util.stream.Collectors;


@Path("xds-document")
public class XDSDocument {

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
            String xInsurantid = pharmacyService.getKVNR(konnektor, egkHandle, null, defaultUserConfig);
            multiEpaService.setXInsurantid(xInsurantid);
            EpaAPI epaAPI = multiEpaService.getEpaAPI();
            String np = idpClient.getVauNpSync(defaultUserConfig);
            epaAPI.setNp(np);
            
            EntitlementRequestType entitlementRequest = new EntitlementRequestType();
			entitlementRequest.setJwt(idpClient.createEntitilementPSJWT(np, defaultUserConfig));
			epaAPI.getEntitlementsApi().setEntitlementPs(xInsurantid, "CLIENTID", entitlementRequest);
            
            RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
            DocumentRequest documentRequest = new DocumentRequest();
            documentRequest.setDocumentUniqueId(UUID.randomUUID().toString());
            documentRequest.setHomeCommunityId("CommunityId");
            documentRequest.setRepositoryUniqueId("UniqueId");
            retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
            if (epaAPI == null) {
                return "No epa found for: " + xInsurantid;
            }
            RetrieveDocumentSetResponseType retrieveDocumentSetResponseType = epaAPI.getDocumentManagementPortType().documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
            return retrieveDocumentSetResponseType.getDocumentResponse().stream()
				.map(RetrieveDocumentSetResponseType.DocumentResponse::getDocumentUniqueId)
				.collect(Collectors.joining(", "));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }

    }
}
