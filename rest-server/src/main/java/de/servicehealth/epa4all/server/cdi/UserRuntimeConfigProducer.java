package de.servicehealth.epa4all.server.cdi;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UserRuntimeConfigProducer implements ContainerRequestFilter  {
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Context
    UriInfo info;
	
	@Inject
	KonnektorDefaultConfig konnektorDefaultConfig;
	
	@de.health.service.cetp.KonnektorConfig
	Map<String, KonnektorConfig> konnektorConfigs;
	
	@RequestScoped
	@FromHttpPath
	@Produces
	public UserRuntimeConfig userRuntimeConfig() {
		if(info.getPathParameters().containsKey("konnektor")) {
			String konnektor = info.getPathParameters().get("konnektor").getFirst();
			Optional<String> configKey = konnektorConfigs.keySet().stream().filter(s -> s.startsWith(konnektor)).findAny();
			if(configKey.isPresent()) {
				IUserConfigurations userConfigurations = konnektorConfigs.get(configKey.get()).getUserConfigurations();
				UserRuntimeConfig userRuntimeConfig = new AppConfig(konnektorDefaultConfig, userConfigurations);
				return userRuntimeConfig;
			}
		}
		return defaultUserConfig;
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		
	}

}
