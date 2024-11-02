package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.cardlink.JwtConfigurator;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class EpaJwtConfigurator extends JwtConfigurator {

    private static final Logger log = Logger.getLogger(EpaJwtConfigurator.class.getName());

    private final IdpClient idpClient;

    public EpaJwtConfigurator(
        UserRuntimeConfig userRuntimeConfig,
        IdpClient idpClient
    ) {
        super(userRuntimeConfig);
        this.idpClient = idpClient;
    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        try {
            idpClient.getBearerToken(userRuntimeConfig, token ->
                headers.put("Authorization", List.of("Bearer " + token))
            );
        } catch (Exception e) {
            log.severe("Error while getting Bearer token -> " + e.getMessage());
        }
    }
}
