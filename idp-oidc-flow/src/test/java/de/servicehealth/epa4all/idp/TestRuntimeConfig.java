package de.servicehealth.epa4all.idp;

import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.IRuntimeConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.config.api.UserRuntimeConfig;
import org.junit.jupiter.api.Test;

public class TestRuntimeConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;

    public TestRuntimeConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;
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
        return null;
    }

    @Override
    public IRuntimeConfig getRuntimeConfig() {
        return null;
    }

    @Override
    public UserRuntimeConfig copy() {
        return null;
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {

    }
}
