package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.Config;
import kong.unirest.core.HttpRequest;
import kong.unirest.core.HttpRequestSummary;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Interceptor;
import kong.unirest.core.Unirest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class IdpClientIT {

    private static final Logger log = LoggerFactory.getLogger(IdpClientIT.class.getName());

    @Inject
    IdpConfig idpConfig;

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
        EpaAPI epaAPI = epaMultiService.findEpaAPI("X110485291");
        String backend = epaAPI.getBackend();

        VauFacade vauFacade = epaAPI.getVauFacade();
        VauClient vauClient = vauFacade.getSessionClients().getFirst();

        String clientId = idpConfig.getClientId();
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        AuthorizationSmcBApi authorizationSmcBApi = epaAPI.getAuthorizationSmcBApi();
        String vauClientUuid = vauClient.getUuid();
        String nonce = authorizationSmcBApi.getNonce(clientId, userAgent, backend, vauClientUuid).getNonce();
        URI location;
        try (Response response = authorizationSmcBApi.sendAuthRequest(clientId, userAgent, backend, vauClientUuid)) {
            location = response.getLocation();
        }

        String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        idpClient.getAuthCode(nonce, location, smcbHandle, defaultUserConfig, (SendAuthCodeSCtype authCode) -> {
            log.info("SendAuthCodeSCtype: " + authCode);
            assertNotNull(authCode);

            String vauNp = authorizationSmcBApi.sendAuthCodeSC(clientId, userAgent, backend, vauClientUuid, authCode).getVauNp();
            log.info("NP: " + vauNp);
            assertNotNull(vauNp);
        });
    }

    @Test
    @Disabled
    public void testGetBearerToken() throws Exception {
        EpaAPI epaAPI = epaMultiService.findEpaAPI("X110485291");
        idpClient.getBearerToken(defaultUserConfig, (String token) -> {
            log.info("Bearer " + token);
            assertNotNull(token);
        });
    }
}