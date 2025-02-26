package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Objects;

import static de.servicehealth.logging.LogContext.withMdcEx;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_SUBJECT;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.PATH;

@SuppressWarnings({"DuplicatedCode", "unused"})
@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

    @Inject
    EpaCallGuard epaCallGuard;

    @APIResponses({
        @APIResponse(description = "ePA response"),
        @APIResponse(responseCode = "400", description = "x-insurantid is missed"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Operation(summary = "Forward client's FHIR GET request to ePA")
    public Response forward(
        @Parameter(
            name = "fhirPath",
            description = "ePA fhir path (doesn't work from swagger-ui)",
            in = PATH,
            allowReserved = true,
            example = "pdf/xhtml/Medication"
        )
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @Parameter(name = X_SUBJECT, description = "Patient KVNR (FHIR compatible)")
        @QueryParam(X_SUBJECT) String subject,
        @Parameter(name = "ui5", description = "Flag of partial updates (JSON Patch) usage, true/false")
        @QueryParam("ui5") String ui5
    ) throws Exception {
        if (xInsurantId == null) {
            log.warn(String.format("[Bad Request] Path %s xInsurantId == null", fhirPath));
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String backend = epaMultiService.findEpaAPI(xInsurantId).getBackend();
        String query = uriInfo.getRequestUri().getQuery();
        Integer hash = Objects.hash(fhirPath, query, xInsurantId);
        return deduplicatedCall(fhirPath, query, hash, () -> epaCallGuard.callAndRetry(backend, () ->
            forward(true, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, null)
        ));
    }

    @APIResponses({
        @APIResponse(description = "ePA response"),
        @APIResponse(responseCode = "400", description = "x-insurantid is missed"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Operation(summary = "Forward client's FHIR POST request to ePA")
    public Response forward(
        @Parameter(
            name = "fhirPath",
            description = "ePA fhir path (doesn't work from swagger-ui)",
            in = PATH,
            allowReserved = true,
            example = "pdf/xhtml/Medication?_count=10&_offset=0&_total=none"
        )
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @Parameter(name = X_SUBJECT, description = "Patient KVNR (FHIR compatible)")
        @QueryParam(X_SUBJECT) String subject,
        @Parameter(name = "ui5", description = "Flag of partial updates (JSON Patch) usage, true/false")
        @QueryParam("ui5") String ui5,
        @Parameter(description = "Payload to submit to ePA", example = "xml/pdf")
        byte[] body
    ) throws Exception {
        if (xInsurantId == null) {
            log.warn(String.format("[Bad Request] Path %s xInsurantId == null", fhirPath));
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String backend = epaMultiService.findEpaAPI(xInsurantId).getBackend();
        String query = uriInfo.getRequestUri().getQuery();
        Integer hash = Objects.hash(fhirPath, query, xInsurantId);
        return deduplicatedCall(fhirPath, query, hash, () -> epaCallGuard.callAndRetry(backend, () ->
            forward(false, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, body)
        ));
    }

    private Response forward(
        boolean isGet,
        boolean ui5,
        String fhirPath,
        UriInfo uriInfo,
        HttpHeaders headers,
        String xInsurantId,
        String subject,
        byte[] body
    ) throws Exception {
        // Use fhir compatible variables
        String insurantId = subject != null ? subject : xInsurantId;
        EpaContext epaContext = prepareEpaContext(insurantId);
        return withMdcEx(epaContext.getMdcMap(), () -> {
            EpaAPI epaAPI = epaMultiService.findEpaAPI(epaContext.getInsurantId());
            String baseQuery = uriInfo.getRequestUri().getQuery();
            return epaAPI.getFhirProxy().forward(
                isGet, ui5, fhirPath, baseQuery, headers, body, epaContext.getXHeaders()
            );
        });
    }
}
