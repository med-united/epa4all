package de.servicehealth.epa4all.server.cetp.cardlink;

import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;

public interface CardlinkClientFactory {

    CardlinkClient build(KonnektorConfig konnektorConfig);
}
