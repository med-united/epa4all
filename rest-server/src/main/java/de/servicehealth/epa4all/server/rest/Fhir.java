package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

@RequestScoped
@Path("fhir")
public class Fhir extends AbstractResource {
	
	@GET
	@Path("pdf/{konnektor : ([0-9a-zA-Z\\-]+)?}")
	public Response pdf(
		@PathParam("konnektor") String konnektor,
		@QueryParam("kvnr") String kvnr
	) {
		try {
			EpaAPI epaAPI = xdsDocumentService.getEpaInsurantPair(telematikId, kvnr, smcbHandle, userRuntimeConfig).getLeft();
			byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(epaAPI.getXInsurantid(), USER_AGENT, epaAPI.getNp());
			return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
	}

	@GET
	@Path("xhtml/{konnektor : ([0-9a-zA-Z\\-]+)?}")
	public Response get(
		@PathParam("konnektor") String konnektor,
		@QueryParam("kvnr") String kvnr
	) {
		try {
			EpaAPI epaAPI = xdsDocumentService.getEpaInsurantPair(telematikId, kvnr, smcbHandle, userRuntimeConfig).getLeft();
			byte[] html = epaAPI.getRenderClient().getXhtmlDocument(epaAPI.getXInsurantid(), USER_AGENT, epaAPI.getNp());
			return Response.ok(html, "text/html").build();
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
	}
}
