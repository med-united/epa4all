package de.servicehealth.epa4all.server.rest.tss;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import de.servicehealth.epa4all.server.tss.TssClient;
import de.servicehealth.epa4all.server.tss.TssException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.http.HttpResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Set;

import static de.servicehealth.vau.VauClient.SCOPE;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

@SuppressWarnings("unused")
@RequestScoped
@Path("/tss")
public class Tss {

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TSSClient
    IdpClient idpClient;

    @Inject
    TssClient tssClient;

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
        return getTssToken(scope);
    }

    private String getTssToken(String scope) throws Exception {
        Set<String> scopesSet = scope == null || scope.trim().isEmpty()
            ? Set.of("Abrechnungsinformation")
            : Set.of(scope);
        String smcbHandle = konnektorClient.getSmcbHandle(userRuntimeConfig);
        return idpClient.getAccessToken(smcbHandle, scopesSet, userRuntimeConfig);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "TSS request was successfully forwarded"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(APPLICATION_XML)
    @Produces(WILDCARD)
    @Operation(summary = "Forward client's TSS POST request to TerminServer")
    public String forward(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(
            name = SCOPE,
            description = "KV.digital supported scope, default is 'Abrechnungsinformation'"
        )
        @QueryParam(SCOPE) String scope,
        @Parameter(description = "XML payload to submit to TerminServer")
        byte[] body
    ) throws Exception {
        String accessToken = getTssToken(scope);
        HttpResponse httpResponse = tssClient.submit(accessToken, body);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode == SC_UNAUTHORIZED ) {
            throw new TssException("401 Unauthorized", UNAUTHORIZED);
        }
        if (statusCode == SC_FORBIDDEN ) {
            throw new TssException("403 Forbidden", FORBIDDEN);
        }
        return new String(httpResponse.getEntity().getContent().readAllBytes(), UTF_8);
    }
}