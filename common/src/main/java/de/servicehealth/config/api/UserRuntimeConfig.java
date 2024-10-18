package de.servicehealth.config.api;

@SuppressWarnings("unused")
public interface UserRuntimeConfig {

    String getConnectorBaseURL();

    String getConnectorVersion();

    String getMandantId();

    String getWorkplaceId();

    String getClientSystemId();

    String getUserId();

    IUserConfigurations getUserConfigurations();

    IRuntimeConfig getRuntimeConfig();

    UserRuntimeConfig copy();

    void updateProperties(IUserConfigurations userConfigurations);
}
