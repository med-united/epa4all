package de.servicehealth.epa4all.server.rest.idp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Set;

import static de.servicehealth.vau.VauClient.SCOPE;

@RequestScoped
@Path("/tss")
public class TSS {

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TSSClient
    IdpClient idpClient;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "TSS accessToken is acquired"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("/token")
    @Operation(summary = "Acquire access token for KV Appointment Service")
    public String getToken(
        @Parameter(
            name = SCOPE,
            description = "KV.digital supported scope, default is 'Abrechnungsinformation'"
        )
        @QueryParam(SCOPE) String scope
    ) throws Exception {
        Set<String> scopesSet = scope == null || scope.trim().isEmpty()
            ? Set.of("Abrechnungsinformation")
            : Set.of(scope);
        String smcbHandle = konnektorClient.getSmcbHandle(userRuntimeConfig);
        return idpClient.getAccessToken(smcbHandle, scopesSet, userRuntimeConfig);
    }
}