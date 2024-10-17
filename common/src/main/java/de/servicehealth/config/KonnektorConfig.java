package de.servicehealth.config;

import de.servicehealth.config.api.IUserConfigurations;
import lombok.Data;

import java.io.File;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;

@Data
public class KonnektorConfig {

    File folder;
    Integer cetpPort;
    URI cardlinkEndpoint;
    String subscriptionId;
    OffsetDateTime subscriptionTime;
    IUserConfigurations userConfigurations;

    private final Semaphore semaphore = new Semaphore(1);

    public KonnektorConfig() {
    }

    public KonnektorConfig(
        File folder,
        Integer cetpPort,
        URI cardlinkEndpoint,
        IUserConfigurations userConfigurations
    ) {
        this.folder = folder;
        this.cetpPort = cetpPort;
        this.cardlinkEndpoint = cardlinkEndpoint;
        this.userConfigurations = userConfigurations;

        subscriptionId = null;
        subscriptionTime = OffsetDateTime.now().minusDays(30);
    }

    public String getHost() {
        String connectorBaseURL = userConfigurations.getConnectorBaseURL();
        return connectorBaseURL == null ? null : connectorBaseURL.split("//")[1];
    }
}

