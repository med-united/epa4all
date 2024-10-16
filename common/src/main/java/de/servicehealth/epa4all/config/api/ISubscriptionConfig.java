package de.servicehealth.epa4all.config.api;

import java.util.Optional;

public interface ISubscriptionConfig {

    int getCetpSubscriptionsMaintenanceRetryIntervalMs();

    int getCetpSubscriptionsRenewalSafePeriodMs();

    int getForceResubscribePeriodSeconds();

    Optional<String> getCardLinkServer(); // default -> used when no config/konnektoren/{port} is absent

    Optional<String> getEventToHost();

    int getCetpServerDefaultPort(); // default -> used when no config/konnektoren/{port} is absent
}
