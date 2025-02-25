package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RequestScoped
@Path("vau")
public class Vau {

    @Inject
    VauNpProvider vauNpProvider;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "VAU sessions reload status JSON"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("reload")
    @Operation(summary = "Force empty VAU sessions to reload")
    public Response reload(
        @Parameter(
            name = "backends",
            description = "The target ePA backend of the affected VAU sessions, all backends if skipped"
        )
        @QueryParam("backends") String backends
    ) throws Exception {
        Set<String> set = Set.of();
        if (backends != null && !backends.isEmpty()) {
            set = Arrays.stream(backends.split(",")).map(String::trim).collect(Collectors.toSet());
        }
        return Response.ok(vauNpProvider.reload(set)).build();
    }
}
