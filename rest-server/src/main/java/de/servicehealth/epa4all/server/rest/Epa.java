package de.servicehealth.epa4all.server.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;

@RequestScoped
@Path("epa")
public class Epa extends AbstractResource {

    @APIResponses({
        @APIResponse(responseCode = "204", description = "The patient has ePA"),
        @APIResponse(responseCode = "404", description = "The patient has no ePA"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Operation(summary = "Check ePA for the KVNR")
    public Response checkEpa(
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String insurantId
    ) {
        Response.Status status = epaMultiService.checkInsurantEPA(insurantId) ? NO_CONTENT : NOT_FOUND;
        return Response.status(status).build();
    }
}