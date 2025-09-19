package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.common.profile.IdpTssProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.serviceport.ServicePortProvider;
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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(IdpTssProfile.class)
public class IdpClientTssIT {

    private static final Logger log = LoggerFactory.getLogger(IdpClientEpaIT.class.getName());

    @Inject
    IdpClient idpClient;

    @Inject
    ClientFactory clientFactory;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    ServicePortProvider servicePortProvider;

    @BeforeEach
    public void before() throws Exception {
        new File("config/konnektoren/discovery-doc").delete();
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
        idpClient.doStart();
        clientFactory.doStart();
        epaMultiService.doStart();
        servicePortProvider.doStart();
    }

    @Test
    public void accessTokenObtained() throws Exception {
        String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        String accessToken = idpClient.getAccessToken(smcbHandle, defaultUserConfig);
        log.info("Bearer " + accessToken);
        assertNotNull(accessToken);
    }
}
