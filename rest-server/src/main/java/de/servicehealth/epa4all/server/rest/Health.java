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

@RequestScoped
@Path("health")
public class Health extends AbstractResource {

    @Context
    HttpServletRequest httpServletRequest;

    @Inject
    HealthChecker healthChecker;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        return Response.ok(healthChecker.getHealthInfo(null)).build();
    }
}
