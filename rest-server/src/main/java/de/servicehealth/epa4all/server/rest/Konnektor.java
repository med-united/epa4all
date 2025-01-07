package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.epa4all.server.config.KonnektorUserConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import java.util.List;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@SuppressWarnings("unused")
@Path("konnektor")
public class Konnektor extends AbstractResource {

    @GET
    @Produces({APPLICATION_JSON, APPLICATION_XML})
    @Path("configs")
    public List<KonnektorUserConfig> configs(
        @HeaderParam(ACCEPT) String accept,
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
