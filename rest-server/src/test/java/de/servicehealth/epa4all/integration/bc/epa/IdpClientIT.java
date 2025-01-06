package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import kong.unirest.core.Config;
import kong.unirest.core.HttpRequest;
import kong.unirest.core.HttpRequestSummary;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Interceptor;
import kong.unirest.core.Unirest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class IdpClientIT {

    private static final Logger log = Logger.getLogger(IdpClientIT.class.getName());

    @Inject
    IdpClient idpClient;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @BeforeEach
    public void before() {
        Unirest.config().interceptor(new Interceptor() {
            @Override
            public void onRequest(HttpRequest<?> request, Config config) {
                log.info("Request: " + request.getBody());
            }

            @Override
            public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
                log.info("Response: " + response.getBody());
            }
        });
    }

    @Test
    public void testGetVauNp() throws Exception {
        EpaAPI epaAPI = epaMultiService.getEpaAPI("X110485291");
        String backend = epaAPI.getBackend();
        String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        idpClient.getVauNp(epaAPI.getAuthorizationSmcBApi(), defaultUserConfig, smcbHandle, backend, (String np) -> {
            log.info("NP: " + np);
            assertNotNull(np);
        });
    }

    @Test
    @Disabled
    public void testGetBearerToken() throws Exception {
        EpaAPI epaAPI = epaMultiService.getEpaAPI("X110485291");
        idpClient.getBearerToken("test:8080", epaAPI.getAuthorizationSmcBApi(), defaultUserConfig, (String token) -> {
            log.info("Bearer " + token);
            assertNotNull(token);
        });
    }
}