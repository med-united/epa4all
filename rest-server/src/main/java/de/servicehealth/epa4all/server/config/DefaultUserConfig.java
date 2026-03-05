package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IRuntimeConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.vsd.VsdConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

@ApplicationScoped
@Default
public class DefaultUserConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;
    private final IUserConfigurations userConfigurations;

    @Inject
    public DefaultUserConfig(KonnektorDefaultConfig konnektorDefaultConfig, VsdConfig vsdConfig) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;
        userConfigurations = konnektorDefaultConfig.toUserConfigurations(vsdConfig.getPrimaryIccsn());
    }

    @Override
    public String getConnectorBaseURL() {
        return konnektorDefaultConfig.getUrl();
    }

    @Override
    public String getConnectorVersion() {
        return konnektorDefaultConfig.getVersion();
    }

    @Override
    public String getMandantId() {
        return konnektorDefaultConfig.getMandantId();
    }

    @Override
    public String getWorkplaceId() {
        return konnektorDefaultConfig.getWorkplaceId();
    }

    @Override
    public String getClientSystemId() {
        return konnektorDefaultConfig.getClientSystemId();
    }

    @Override
    public String getUserId() {
        return konnektorDefaultConfig.getUserId().orElse(null);
    }

    @Override
    public IUserConfigurations getUserConfigurations() {
        return userConfigurations;
    }

    @Override
    public UserRuntimeConfig copy() {
        return new RuntimeConfig(konnektorDefaultConfig, userConfigurations);
    }

    @Override
    public IRuntimeConfig getRuntimeConfig() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
