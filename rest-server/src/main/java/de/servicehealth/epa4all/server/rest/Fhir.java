package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@RequestScoped
@Path("{fhirPath: fhir/.*}")
public class Fhir extends AbstractResource {

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
    ) {
        return forward(true, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, null);
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
    ) {
        return forward(false, Boolean.parseBoolean(ui5), fhirPath, uriInfo, httpHeaders, xInsurantId, subject, body);
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
    ) {
        try {
            // Use fhir compatible variables
            if (subject != null) {
                xInsurantId = subject;
            }

            EpaContext epaContext = prepareEpaContext(xInsurantId);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            return epaAPI.getFhirProxy().forward(isGet, ui5, fhirPath, uriInfo, headers, body, epaContext.getXHeaders());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
