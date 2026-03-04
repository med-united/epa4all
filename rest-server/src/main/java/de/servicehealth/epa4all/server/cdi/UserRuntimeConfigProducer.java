package de.servicehealth.epa4all.server.cdi;

import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.konnektorconfig.KonnektorsConfigs;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_SMCB_ICCSN;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;

@Provider
public class UserRuntimeConfigProducer implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserRuntimeConfigProducer.class.getName());

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Context
    UriInfo info;

    @Context
    HttpHeaders httpHeaders;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    KonnektorsConfigs konnektorsConfigs;

    @RequestScoped
    @FromHttpPath
    @Produces
    public UserRuntimeConfig userRuntimeConfig() {
        String konnektor = getQueryParameter(X_KONNEKTOR);
        String workplaceId = getQueryParameter(X_WORKPLACE);
        String smcbIccsn = httpHeaders.getHeaderString(X_SMCB_ICCSN);
        String requestIccsn = smcbIccsn == null || smcbIccsn.trim().isEmpty() ? null : smcbIccsn.split(",")[0];
        
        List<KonnektorConfig> foundConfigs = konnektorsConfigs.filterConfigs(konnektor, workplaceId);
        if (foundConfigs.isEmpty()) {
            String msg = "No konnektor configs found for konnektor=%s workplace=%s, using default";
            log.warn(String.format(msg, konnektor, workplaceId));
            return requestIccsn == null ? defaultUserConfig : new RuntimeConfig(konnektorDefaultConfig, requestIccsn);
        }

        if (foundConfigs.size() > 1) {
            String msg = "Multiple KonnektorConfigs found for konnektor=%s workplace=%s, using first one";
            log.warn(String.format(msg, konnektor, workplaceId));
        }

        IUserConfigurations userConfigurations = foundConfigs.getFirst().getUserConfigurations();
        if (requestIccsn != null) {
            userConfigurations.setIccsn(requestIccsn);
        }
        return new RuntimeConfig(konnektorDefaultConfig, userConfigurations);
    }

    private String getQueryParameter(String parameterName) {
        MultivaluedMap<String, String> queryParameters = info.getQueryParameters();
        return queryParameters.containsKey(parameterName) ? queryParameters.get(parameterName).getFirst(): null;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
    }
}
