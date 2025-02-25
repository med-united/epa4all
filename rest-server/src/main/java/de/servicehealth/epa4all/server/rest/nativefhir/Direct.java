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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.io.ByteArrayInputStream;
import java.util.List;

import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path("direct")
public class Direct extends AbstractResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "ePA medication JSON"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("medication")
    @Operation(
        summary = "Get patient medications JSON using native FHIR (feature.native-fhir.enabled=true)",
        deprecated = true
    )
    public Response medication(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.findEpaAPI(epaContext.getInsurantId());
        IMedicationClient client = epaAPI.getMedicationClient().withXHeaders(epaContext.getXHeaders());

        Patient patient = client.searchPatients(kvnr).getLast();
        List<Medication> medications = client.searchMedications();

        return Response.ok(medications).build();
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "ePA medication PDF"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("pdf")
    @Operation(
        summary = "Get patient medications PDF using native FHIR (feature.native-fhir.enabled=true)",
        deprecated = true
    )
    public Response pdf(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.findEpaAPI(epaContext.getInsurantId());

        byte[] pdfBytes = epaAPI.getRenderClient().getPdfBytes(epaContext.getXHeaders());
        return Response.ok(new ByteArrayInputStream(pdfBytes), "application/pdf").build();
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "ePA medication XHTML"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("xhtml")
    @Operation(
        summary = "Get patient medications XHTML using native FHIR (feature.native-fhir.enabled=true)",
        deprecated = true
    )
    public Response get(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        EpaAPI epaAPI = epaMultiService.findEpaAPI(epaContext.getInsurantId());

        byte[] html = epaAPI.getRenderClient().getXhtmlDocument(epaContext.getXHeaders());
        return Response.ok(html, "text/html").build();
    }
}
