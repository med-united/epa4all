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

import java.util.Objects;

import static de.servicehealth.logging.LogContext.withMdcEx;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static jakarta.ws.rs.core.MediaType.WILDCARD;

@SuppressWarnings("DuplicatedCode")
@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

    @Inject
    EpaCallGuard epaCallGuard;

    @GET
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @QueryParam("subject") String subject,
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

    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        @QueryParam("subject") String subject,
        @QueryParam("ui5") String ui5,
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
            String konnektor = userRuntimeConfig.getKonnektorHost();
            String workplace = userRuntimeConfig.getWorkplaceId();
            return epaAPI.getFhirProxy().forward(
                isGet, ui5, fhirPath, baseQuery, konnektor, workplace, headers, body, epaContext.getXHeaders()
            );
        });
    }
}
