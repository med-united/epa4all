package de.servicehealth.epa4all.server.cetp.cardlink;

import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
@IfBuildProperty(name = "feature.cardlink.enabled", stringValue = "false")
public class CardlinkClientStubFactory implements CardlinkClientFactory {

    private static final Logger log = LoggerFactory.getLogger(CardlinkClientStubFactory.class.getName());

    @Override
    public CardlinkClient build(KonnektorConfig konnektorConfig) {
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
