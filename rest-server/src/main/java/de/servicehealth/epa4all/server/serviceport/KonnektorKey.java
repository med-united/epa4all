package de.servicehealth.epa4all.server.serviceport;

import de.health.service.config.api.IUserConfigurations;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class KonnektorKey {

    private final String mandantId;
    private final String workplaceId;
    private final String clientSystemId;
    private final String konnektorBaseUrl;

    public KonnektorKey(IUserConfigurations userConfigurations) {
        mandantId = userConfigurations.getMandantId();
        workplaceId = userConfigurations.getWorkplaceId();
        clientSystemId = userConfigurations.getClientSystemId();
        konnektorBaseUrl = userConfigurations.getConnectorBaseURL();
    }
}
