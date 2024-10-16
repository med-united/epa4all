package de.servicehealth.epa4all.config;

import de.servicehealth.epa4all.config.api.IUserConfigurations;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;

@Getter
@Setter
public class KonnektorConfig {

    File folder;
    Integer cetpPort;
    URI cardlinkEndpoint;
    IUserConfigurations userConfigurations;
    String subscriptionId;
    OffsetDateTime subscriptionTime;

    private final Semaphore semaphore = new Semaphore(1);

    public KonnektorConfig() {
    }

    public KonnektorConfig(
        File folder,
        Integer cetpPort,
        IUserConfigurations userConfigurations,
        URI cardlinkEndpoint
    ) {
        this.folder = folder;
        this.cetpPort = cetpPort;
        this.userConfigurations = userConfigurations;
        this.cardlinkEndpoint = cardlinkEndpoint;

        subscriptionId = null;
        subscriptionTime = OffsetDateTime.now().minusDays(30);
    }

    public String getHost() {
        String connectorBaseURL = userConfigurations.getConnectorBaseURL();
        return connectorBaseURL == null ? null : connectorBaseURL.split("//")[1];
    }
}

