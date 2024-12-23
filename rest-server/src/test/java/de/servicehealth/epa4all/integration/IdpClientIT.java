package de.servicehealth.epa4all.integration;

import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import io.quarkus.test.junit.QuarkusTest;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class IdpClientIT {

    @Inject
    IdpClient idpClient;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @BeforeEach
    public void before() {
        Unirest.config().interceptor(new Interceptor() {
            @Override
            public void onRequest(HttpRequest<?> request, Config config) {
                System.out.println("Request: " + request.getBody());
            }

            @Override
            public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
                System.out.println("Response: " + response.getBody());
            }
        });
    }

    @Test
    public void testGetVauNp() throws Exception {
        EpaAPI epaAPI = epaMultiService.getEpaAPI("X110485291");
        String backend = epaAPI.getBackend();
        idpClient.getVauNp(epaAPI.getAuthorizationSmcBApi(), defaultUserConfig, "SMC-B-12", backend, (String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
    }

    @Test
    @Disabled
    public void testGetBearerToken() throws Exception {
        EpaAPI epaAPI = epaMultiService.getEpaAPI("X110485291");
        idpClient.getBearerToken("test:8080", epaAPI.getAuthorizationSmcBApi(), defaultUserConfig, (String token) -> {
            System.out.println("Bearer " + token);
            assertNotNull(token);
        });
    }
}