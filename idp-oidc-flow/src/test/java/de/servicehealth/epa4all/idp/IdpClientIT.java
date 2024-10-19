package de.servicehealth.epa4all.idp;

import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.UserRuntimeConfig;
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

public abstract class IdpClientIT {

    @Inject
    IdpClient idpClient;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @BeforeEach
    public void before() {
        Unirest.config().interceptor(new Interceptor() {
            @Override
            public void onRequest(HttpRequest<?> request, Config config) {
                System.out.println("Request: " + request);
            }

            @Override
            public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
                System.out.println("Response: " + response);
            }
        });
    }

    @Test
    public void testGetVauNp() throws Exception {
        UserRuntimeConfig runtimeConfig = new TestRuntimeConfig(konnektorDefaultConfig);
        idpClient.getVauNp(runtimeConfig, (String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
    }

    @Test
    public void testGetBearerToken() throws Exception {
        UserRuntimeConfig runtimeConfig = new TestRuntimeConfig(konnektorDefaultConfig);
        idpClient.getBearerToken(runtimeConfig, (String token) -> {
            System.out.println("Bearer " + token);
            assertNotNull(token);
        });
    }
}