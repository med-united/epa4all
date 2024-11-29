package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.io.ByteArrayInputStream;
import java.util.List;

@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @QueryParam("{x-konnektor : ([0-9a-zA-Z\\-\\.]+)?}") String konnektor,
        @QueryParam("x-insurantid") String xInsurantId
    ) {
    	if(fhirPath != null && fhirPath.startsWith("fhir/xhtml")) {
    		return xhtml(konnektor, xInsurantId);
    	} else if(fhirPath != null && fhirPath.startsWith("fhir/pdf")) {
        		return pdf(konnektor, xInsurantId);
    	} else {
    		return forward(true, fhirPath, uriInfo, xInsurantId, null);
    	}
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @QueryParam("{x-konnektor : ([0-9a-zA-Z\\-\\.]+)?}") String konnektor,
        @QueryParam("x-insurantid") String xInsurantId,
        byte[] body
    ) {
        return forward(false, fhirPath, uriInfo, xInsurantId, body);
    }

    private Response forward(boolean isGet, String fhirPath, UriInfo uriInfo, String xInsurantId, byte[] body) {
        try {
            EpaContext epaContext = prepareEpaContext(xInsurantId);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            return epaAPI.getFhirProxy().forward(isGet, fhirPath, uriInfo, body, epaContext.getXHeaders());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("medication/{konnektor : ([0-9a-zA-Z\\-.]+)?}")
    public Response medication(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            IMedicationClient client = epaAPI.getMedicationClient(epaContext.getXHeaders());

            Patient patient = client.searchPatients(kvnr).getLast();
            List<Medication> medications = client.searchMedications(patient);

            return Response.ok(medications).build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    public Response pdf(
        String konnektor,
        String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

            byte[] pdfBytes = epaAPI.getRenderClient(epaContext.getXHeaders()).getPdfBytes();
            return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    public Response xhtml(
        String konnektor,
        String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

            byte[] html = epaAPI.getRenderClient(epaContext.getXHeaders()).getXhtmlDocument();
            return Response.ok(html, "text/html").build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
