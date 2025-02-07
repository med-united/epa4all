package de.servicehealth.epa4all.server.cetp.cardlink;

import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.EpaJwtConfigurator;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class CardlinkClientWSFactory {

    private static final Logger log = LoggerFactory.getLogger(CardlinkClientWSFactory.class.getName());

    private final FeatureConfig featureConfig;
    private final IdpClient idpClient;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CardlinkClientWSFactory(
        IdpClient idpClient,
        FeatureConfig featureConfig,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.featureConfig = featureConfig;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    public CardlinkClient build(KonnektorConfig konnektorConfig) {
        if (featureConfig.isCardlinkEnabled()) {
            RuntimeConfig runtimeConfig = new RuntimeConfig(
                konnektorDefaultConfig,
                konnektorConfig.getUserConfigurations()
            );
            return new CardlinkWebsocketClient(
                konnektorConfig.getCardlinkEndpoint(),
                new EpaJwtConfigurator(runtimeConfig, idpClient)
            );
        } else {
            return new CardlinkClient() {
                @Override
                public void connect() {
                    log.info("[Stub] CardlinkClient connect");
                }

                @Override
                public Supplier<Boolean> connected() {
                    return () -> false;
                }

                @Override
                public void sendJson(String correlationId, String iccsn, String type, Map<String, Object> payloadMap) {
                    log.info("[Stub] CardlinkClient sendJson");
                }

                @Override
                public void close() {
                    log.info("[Stub] CardlinkClient close");
                }
            };
        }
    }
}
