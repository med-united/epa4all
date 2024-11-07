package de.servicehealth.epa4all.server.cdi;

import java.util.Map;

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
import jakarta.ws.rs.core.Context;

public class UserRuntimeConfigProducer {
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Context
	HttpServletRequest httpServletRequest;
	
	@Inject
	KonnektorDefaultConfig konnektorDefaultConfig;
	
	@de.health.service.cetp.KonnektorConfig
	Map<String, KonnektorConfig> konnektorConfigs;
	
	@RequestScoped
	@FromHttpPath
	@Produces
	public UserRuntimeConfig userRuntimeConfig() {
		String[] parts = httpServletRequest.getServletPath().split("/");
		String konnektor = parts[0];
		if(konnektorConfigs.containsKey(konnektor)) {
			IUserConfigurations userConfigurations = konnektorConfigs.get(konnektor).getUserConfigurations();
			UserRuntimeConfig userRuntimeConfig = new AppConfig(konnektorDefaultConfig, userConfigurations);
			return userRuntimeConfig;
		}
		return defaultUserConfig;
	}

}
