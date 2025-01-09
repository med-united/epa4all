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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

    @Inject
    EpaCallGuard epaCallGuard;

    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @QueryParam("subject") String subject,
        @QueryParam("ui5") String ui5
    ) throws Exception {
        String backend = epaMultiService.getEpaAPI(xInsurantId).getBackend();
        return epaCallGuard.callAndRetry(backend, () ->
            forward(true, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, null)
        );
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @QueryParam("subject") String subject,
        @QueryParam("ui5") String ui5,
        byte[] body
    ) throws Exception {
        String backend = epaMultiService.getEpaAPI(xInsurantId).getBackend();
        return epaCallGuard.callAndRetry(backend, () ->
            forward(false, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, body)
        );
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
        if (subject != null) {
            xInsurantId = subject;
        }
        log.info(String.format("[%s] FHIR [%s] is forwarding", Thread.currentThread().getName(), fhirPath));
        EpaContext epaContext = prepareEpaContext(xInsurantId);
        EpaAPI epaAPI = epaMultiService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        String baseQuery = uriInfo.getRequestUri().getQuery();
        return epaAPI.getFhirProxy().forward(isGet, ui5, fhirPath, baseQuery, headers, body, epaContext.getXHeaders());
    }
}
