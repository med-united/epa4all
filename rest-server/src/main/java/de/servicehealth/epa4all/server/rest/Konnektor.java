package de.servicehealth.epa4all.server.rest;

import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.epa4all.server.config.KonnektorUserConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Map;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@SuppressWarnings("unused")
@Path("konnektor")
public class Konnektor extends AbstractResource {

    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "List of Konnektor configurations"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces({APPLICATION_JSON, APPLICATION_XML})
    @Path("configs")
    @Operation(summary = "Return current epa4all Konnektor configurations")
    public List<KonnektorUserConfig> configs(
        @HeaderParam(ACCEPT) String accept,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor
    ) {
        return konnektorsConfigs.values().stream()
            .filter(kc -> konnektor == null || (kc.getHost() != null && kc.getHost().contains(konnektor)))
            .map(kc -> {
                IUserConfigurations userConfigurations = kc.getUserConfigurations();
                return new KonnektorUserConfig(
                    userConfigurations.getClientCertificate(),
                    userConfigurations.getClientSystemId(),
                    userConfigurations.getConnectorBaseURL(),
                    userConfigurations.getMandantId(),
                    userConfigurations.getUserId(),
                    userConfigurations.getVersion(),
                    userConfigurations.getWorkplaceId(),
                    kc.getCardlinkEndpoint().toString()
                );
            })
            .toList();
    }
}
