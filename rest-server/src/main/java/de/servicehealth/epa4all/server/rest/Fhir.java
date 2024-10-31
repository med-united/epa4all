package de.servicehealth.epa4all.server.rest;

import java.io.ByteArrayInputStream;

import de.service.health.api.epa4all.EpaAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Path("fhir")
public class Fhir extends AbstractResource {
	

	@GET
	@Path("{konnektor : (\\w+)?}{egkHandle : (/\\w+)?}")
	public Response get(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle) {
		try {
			EpaAPI epaAPI = initAndGetEpaAPI(konnektor, egkHandle);
			
			byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(epaAPI.getXInsurantid(), "ClientID", epaAPI.getNp());
			return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
		
	}
}
