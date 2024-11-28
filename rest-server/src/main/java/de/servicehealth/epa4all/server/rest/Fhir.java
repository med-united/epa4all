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
        try {
            EpaContext epaContext = prepareEpaContext(xInsurantId);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            return epaAPI.getFhirProxy().forward(fhirPath, uriInfo, null, epaContext.getRuntimeAttributes());
        } catch (Exception e) {
            throw new WebApplicationException(e);
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
        try {
            EpaContext epaContext = prepareEpaContext(xInsurantId);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            return epaAPI.getFhirProxy().forward(fhirPath, uriInfo, body, epaContext.getRuntimeAttributes());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("medication/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public Response medication(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            IMedicationClient client = epaAPI.getMedicationClient(epaContext.getRuntimeAttributes());

            Patient patient = client.searchPatients(kvnr).getLast();
            List<Medication> medications = client.searchMedications(patient);

            return Response.ok(medications).build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("pdf/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public Response pdf(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

            byte[] pdfBytes = epaAPI.getRenderClient(epaContext.getRuntimeAttributes()).getPdfBytes();
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
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

            byte[] html = epaAPI.getRenderClient(epaContext.getRuntimeAttributes()).getXhtmlDocument();
            return Response.ok(html, "text/html").build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
