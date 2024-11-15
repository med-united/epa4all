package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.util.UUID;

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
			String correlationId = UUID.randomUUID().toString();

			InsuranceData insuranceData = insuranceDataService.getInsuranceData(
				telematikId, kvnr, correlationId, smcbHandle, userRuntimeConfig
			);
			EpaAPI epaAPI = xdsDocumentService.setEntitlementAndGetEpaAPI(userRuntimeConfig, insuranceData, smcbHandle);

			byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(prepareRuntimeAttributes(insuranceData));
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
			String correlationId = UUID.randomUUID().toString();
			InsuranceData insuranceData = insuranceDataService.getInsuranceData(
				telematikId, kvnr, correlationId, smcbHandle, userRuntimeConfig
			);
			EpaAPI epaAPI = xdsDocumentService.setEntitlementAndGetEpaAPI(userRuntimeConfig, insuranceData, smcbHandle);
			byte[] html = epaAPI.getRenderClient().getXhtmlDocument(prepareRuntimeAttributes(insuranceData));
			return Response.ok(html, "text/html").build();
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
	}
}
