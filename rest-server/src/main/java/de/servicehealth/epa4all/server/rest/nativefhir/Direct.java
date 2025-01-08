package de.servicehealth.epa4all.server.rest.nativefhir;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.server.rest.AbstractResource;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.io.ByteArrayInputStream;
import java.util.List;

import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path("direct")
public class Direct extends AbstractResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("medication")
    public Response medication(
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        IMedicationClient client = epaAPI.getMedicationClient().withXHeaders(epaContext.getXHeaders());

        Patient patient = client.searchPatients(kvnr).getLast();
        List<Medication> medications = client.searchMedications();

        return Response.ok(medications).build();
    }

    @GET
    @Path("pdf")
    public Response pdf(
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

        byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(epaContext.getXHeaders());
        return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
    }

    @GET
    @Path("xhtml")
    public Response get(
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());

        byte[] html = epaAPI.getRenderClient().getXhtmlDocument(epaContext.getXHeaders());
        return Response.ok(html, "text/html").build();
    }
}
