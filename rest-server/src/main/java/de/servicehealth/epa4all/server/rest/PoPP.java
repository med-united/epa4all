package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import static jakarta.ws.rs.core.MediaType.WILDCARD;

@SuppressWarnings("unused")
@RequestScoped
@Path("popp")
public class PoPP extends AbstractResource {

    @Inject
    protected EntitlementService entitlementService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "The patient entitlement was successfully created"),
        @APIResponse(responseCode = "400", description = "x-insurantid is invalid"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Path("token")
    @Operation(summary = "Set entitlement for KVNR using PNW")
    public Response setEntitlement(
        String poppToken
    ) throws Exception {
        return Response.ok(entitlementService.setEntitlementV2(poppToken)).build();
    }
}
