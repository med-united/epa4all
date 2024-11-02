package de.servicehealth.config.api;

@SuppressWarnings("unused")
public interface UserRuntimeConfig {
	
    // PU epa.health/1.0.0 ServiceHealthGmbH/GEMIncenereS2QmFN83P
    static String USER_AGENT = "GEMIncenereSud1PErUR/1.0.0";

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

	public static String getUserAgent() {
		return USER_AGENT;
	}
}
