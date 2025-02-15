package de.servicehealth.epa4all.server.serviceport;

import de.health.service.config.api.UserRuntimeConfig;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class KonnektorKey {

    private final String mandantId;
    private final String workplaceId;
    private final String clientSystemId;
    private final String konnektorBaseUrl;

    public KonnektorKey(UserRuntimeConfig userRuntimeConfig) {
        mandantId = userRuntimeConfig.getMandantId();
        workplaceId = userRuntimeConfig.getWorkplaceId();
        clientSystemId = userRuntimeConfig.getClientSystemId();
        konnektorBaseUrl = userRuntimeConfig.getConnectorBaseURL();
    }
}
