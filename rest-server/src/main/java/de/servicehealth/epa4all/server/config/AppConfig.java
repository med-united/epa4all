package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IRuntimeConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;

public class AppConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;
    private IUserConfigurations userConfigurations;

    public AppConfig(KonnektorDefaultConfig konnektorDefaultConfig, IUserConfigurations configurations) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;
        this.userConfigurations = configurations;
    }

    @Override
    public String getConnectorBaseURL() {
        return getOrDefault(userConfigurations.getConnectorBaseURL(), konnektorDefaultConfig.getUrl());
    }

    @Override
    public String getConnectorVersion() {
        return getOrDefault(userConfigurations.getVersion(), konnektorDefaultConfig.getVersion());
    }

    @Override
    public String getMandantId() {
        return getOrDefault(userConfigurations.getMandantId(), konnektorDefaultConfig.getMandantId());
    }

    @Override
    public String getWorkplaceId() {
        return getOrDefault(userConfigurations.getWorkplaceId(), konnektorDefaultConfig.getWorkplaceId());
    }

    @Override
    public String getClientSystemId() {
        return getOrDefault(userConfigurations.getClientSystemId(), konnektorDefaultConfig.getClientSystemId());
    }

    @Override
    public String getUserId() {
        return getOrDefault(userConfigurations.getUserId(), konnektorDefaultConfig.getUserId().orElse(null));
    }

    @Override
    public IUserConfigurations getUserConfigurations() {
        return userConfigurations;
    }

    @Override
    public IRuntimeConfig getRuntimeConfig() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public UserRuntimeConfig copy() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {
        this.userConfigurations = userConfigurations;
    }

    private String getOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
