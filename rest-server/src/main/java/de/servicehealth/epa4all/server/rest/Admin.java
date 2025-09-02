package de.servicehealth.epa4all.server.rest;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@RequestScoped
@Path("admin")
public class Admin {

    @APIResponses({
        @APIResponse(responseCode = "202", description = "Shutdown request accepted"),
    })
    @POST
    @Path("shutdown")
    @Operation(description = "Shutdown epa4all service")
    public Response shutdown() {
        Quarkus.asyncExit();
        return Response.accepted().build();
    }
}