package de.servicehealth.epa4all.server.cdi;

import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;

@Provider
public class UserRuntimeConfigProducer implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserRuntimeConfigProducer.class.getName());

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Context
    UriInfo info;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @RequestScoped
    @FromHttpPath
    @Produces
    public UserRuntimeConfig userRuntimeConfig() {
        String konnektor = getQueryParameter(X_KONNEKTOR);
        String workplaceId = getQueryParameter(X_WORKPLACE);
        List<KonnektorConfig> foundConfigs = konnektorsConfigs.entrySet().stream()
            .filter(e -> konnektor == null || e.getKey().startsWith(konnektor))
            .filter(e -> workplaceId == null || e.getKey().endsWith(workplaceId))
            .map(Map.Entry::getValue)
            .toList();

        if (foundConfigs.size() != 1) {
            String c = foundConfigs.isEmpty() ? "Zero" : "Multiple";
            String msg = "%s KonnektorConfigs found for konnektor=%s workplace=%s, using default";
            log.warn(String.format(msg, c, konnektor, workplaceId));
            return defaultUserConfig;
        }
        return new RuntimeConfig(konnektorDefaultConfig, foundConfigs.getFirst().getUserConfigurations());
    }

    private String getQueryParameter(String parameterName) {
        MultivaluedMap<String, String> queryParameters = info.getQueryParameters();
        return queryParameters.containsKey(parameterName) ? queryParameters.get(parameterName).getFirst(): null;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
    }
}
