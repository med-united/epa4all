package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.CETPServer;
import de.servicehealth.config.api.ISubscriptionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Getter
@ApplicationScoped
public class SubscriptionConfig implements ISubscriptionConfig {

    @ConfigProperty(name = "cetp.subscriptions.renewal.safe.period.seconds")
    Optional<Integer> cetpSubscriptionsRenewalSafePeriodSeconds;

    @ConfigProperty(name = "cetp.subscriptions.maintenance.retry.interval.ms")
    Optional<Integer> cetpSubscriptionsMaintenanceRetryIntervalMs;

    @ConfigProperty(name = "cetp.subscriptions.force.resubscribe.period.seconds")
    Optional<Integer> forceResubscribePeriodSeconds;

    @ConfigProperty(name = "cetp.subscriptions.maintenance.interval.sec")
    Optional<String> cetpSubscriptionsMaintenanceIntervalSec; // 3s

    @ConfigProperty(name = "cetp.subscriptions.default.event-to-host")
    String defaultEventToHost;

    @ConfigProperty(name = "cetp.subscriptions.default.cardlink.server.url")
    Optional<String> defaultCardLinkServer;

    @ConfigProperty(name = "cetp.subscriptions.default.cetp.server.port")
    Optional<Integer> defaultCetpServerPort;

    public int getCetpSubscriptionsRenewalSafePeriodMs() {
        return cetpSubscriptionsRenewalSafePeriodSeconds.orElse(600) * 1000;
    }

    public int getCetpSubscriptionsMaintenanceRetryIntervalMs() {
        return cetpSubscriptionsMaintenanceRetryIntervalMs.orElse(5000);
    }

    public int getForceResubscribePeriodSeconds() {
        return forceResubscribePeriodSeconds.orElse(43200);
    }

    public int getDefaultCetpServerPort() {
        return defaultCetpServerPort.orElse(CETPServer.DEFAULT_PORT);
    }

    public String getDefaultCardLinkServer() {
        return defaultCardLinkServer.orElse(
            "wss://cardlink.service-health.de:8444/websocket/80276003650110006580-20230112"
        );
    }
}
