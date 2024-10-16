package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.CETPServer;
import de.servicehealth.epa4all.config.api.ISubscriptionConfig;
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

    @ConfigProperty(name = "cetp.subscriptions.event-to-host")
    Optional<String> eventToHost;

    @ConfigProperty(name = "cetp.subscriptions.default.cardlink.server.url")
    Optional<String> cardLinkServer;

    @ConfigProperty(name = "cetp.subscriptions.cetp.server.default.port")
    Optional<Integer> cetpServerDefaultPort;

    public int getCetpSubscriptionsRenewalSafePeriodMs() {
        return cetpSubscriptionsRenewalSafePeriodSeconds.orElse(600) * 1000;
    }

    public int getCetpSubscriptionsMaintenanceRetryIntervalMs() {
        return cetpSubscriptionsMaintenanceRetryIntervalMs.orElse(5000);
    }

    public int getForceResubscribePeriodSeconds() {
        return forceResubscribePeriodSeconds.orElse(43200);
    }

    public int getCetpServerDefaultPort() {
        return cetpServerDefaultPort.orElse(CETPServer.DEFAULT_PORT);
    }
}
