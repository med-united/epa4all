package de.servicehealth.epa4all.idp;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class IdpClientIT {

    @Inject
    IdpClient idpClient;

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
        idpClient.getVauNp(defaultUserConfig, "X110485291", "SMC-B-187", (String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
    }

    @Test
    public void testGetBearerToken() throws Exception {
        idpClient.getBearerToken(defaultUserConfig, "X110485291", (String token) -> {
            System.out.println("Bearer " + token);
            assertNotNull(token);
        });
    }
}