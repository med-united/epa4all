package de.servicehealth.epa4all.server.rest;

import de.health.service.cetp.retry.Retrier;
import de.service.health.api.epa4all.EpaAPI;
import jakarta.enterprise.context.RequestScoped;
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

import java.util.List;

import static de.servicehealth.vau.VauClient.VAU_NO_SESSION;

@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

    private static final int FHIR_RETRY_PERIOD_MS = 30000;
    private static final List<Integer> FHIR_RETRIES = List.of(1000, 2000, 3000, 5000);

    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam("{x-konnektor : ([0-9a-zA-Z\\-\\.]+)?}") String konnektor,
        @QueryParam("x-insurantid") String xInsurantId,
        @QueryParam("subject") String subject,
        @QueryParam("ui5") String ui5
    ) throws Exception {
        return Retrier.callAndRetryEx(
            FHIR_RETRIES,
            FHIR_RETRY_PERIOD_MS,
            true,
            () -> forward(true, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, null),
            response -> response.getHeaderString(VAU_NO_SESSION) == null
        );
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxy(
        @PathParam("fhirPath") String fhirPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @QueryParam("{x-konnektor : ([0-9a-zA-Z\\-\\.]+)?}") String konnektor,
        @QueryParam("x-insurantid") String xInsurantId,
        @QueryParam("subject") String subject,
        @QueryParam("ui5") String ui5,
        byte[] body
    ) throws Exception {
        return Retrier.callAndRetryEx(
            FHIR_RETRIES,
            FHIR_RETRY_PERIOD_MS,
            true,
            () -> forward(false, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, body),
            response -> response.getHeaderString(VAU_NO_SESSION) == null
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
        return epaAPI.getFhirProxy().forward(isGet, ui5, fhirPath, uriInfo, headers, body, epaContext.getXHeaders());
    }
}
