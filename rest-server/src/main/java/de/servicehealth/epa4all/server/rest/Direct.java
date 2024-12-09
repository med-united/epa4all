package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.io.ByteArrayInputStream;
import java.util.List;

@RequestScoped
@Path("direct")
public class Direct extends AbstractResource {

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
            IMedicationClient client = epaAPI.getMedicationClient().withXHeaders(epaContext.getXHeaders());

            Patient patient = client.searchPatients(kvnr).getLast();
            List<Medication> medications = client.searchMedications();

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

            byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(epaContext.getXHeaders());
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

            byte[] html = epaAPI.getRenderClient().getXhtmlDocument(epaContext.getXHeaders());
            return Response.ok(html, "text/html").build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
