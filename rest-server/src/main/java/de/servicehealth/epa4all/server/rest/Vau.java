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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RequestScoped
@Path("vau")
public class Vau {

    @Inject
    VauNpProvider vauNpProvider;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("reload")
    public Response reload(
        @QueryParam("backends") String backends
    ) throws Exception {
        Set<String> set = backends == null || backends.isEmpty()
            ? Set.of()
            : Arrays.stream(backends.split(",")).collect(Collectors.toSet());
        return Response.ok(vauNpProvider.reload(set)).build();
    }
}
