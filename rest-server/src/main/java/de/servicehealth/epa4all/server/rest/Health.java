package de.servicehealth.epa4all.server.rest;

import de.health.service.check.HealthChecker;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@RequestScoped
@Path("health")
public class Health extends AbstractResource {

    @Context
    HttpServletRequest httpServletRequest;

    @Inject
    HealthChecker healthChecker;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "epa4all HealthInfo response"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Return epa4all health checks")
    public Response health() {
        return Response.ok(healthChecker.getHealthInfo(null)).build();
    }
}
