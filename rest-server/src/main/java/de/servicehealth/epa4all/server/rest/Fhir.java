package de.servicehealth.epa4all.server.rest;

import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;

import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.idp.IdpClient;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.pharmacy.PharmacyService;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


@Path("fhir")
public class Fhir {
	
	@Inject
	PharmacyService pharmacyService;
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Inject
    MultiEpaService multiEpaService;
	
	@Inject
	IdpClient idpClient;

	@GET
	@Path("{konnektor : (\\w+)?}{egkHandle : (/\\w+)?}")
	public Response get(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle) {
		try {
			String xInsurantid = pharmacyService.getKVNR(konnektor, egkHandle, null, defaultUserConfig);
			EpaAPI epaAPI = multiEpaService.getEpaAPI(xInsurantid);
			if(epaAPI == null) {
				return Response.serverError().entity("No epa found for: "+xInsurantid).build();
			}
			String np = idpClient.getVauNpSync(defaultUserConfig);
			
			byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(xInsurantid, "ClientID", np);
			return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
		
	}
}
