package de.servicehealth.epa4all.server.cetp.cardlink;

import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.server.cetp.EpaJwtConfigurator;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "feature.cardlink.enabled", stringValue = "true", enableIfMissing = true)
public class CardlinkClientWSFactory implements CardlinkClientFactory {

    private final IdpClient idpClient;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CardlinkClientWSFactory(IdpClient idpClient, KonnektorDefaultConfig konnektorDefaultConfig) {
        this.idpClient = idpClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public CardlinkClient build(KonnektorConfig konnektorConfig) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(
            konnektorDefaultConfig,
            konnektorConfig.getUserConfigurations()
        );
        return new CardlinkWebsocketClient(
            konnektorConfig.getCardlinkEndpoint(),
            new EpaJwtConfigurator(runtimeConfig, idpClient)
        );
    }
}
