package de.servicehealth.epa4all.auth;

import de.gematik.vau.lib.VauClientStateMachine;
import de.service.health.api.epa4all.EpaConfig;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.DockerAction;
import de.servicehealth.epa4all.common.Utils;
import de.servicehealth.model.GetNonce200Response;
import de.servicehealth.vau.VauClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractAuthTest {

    public static final String AUTHORIZATION_SERVICE = "authorization-service";

    String xUseragent = "CLIENTID1234567890AB/2.1.12-45";

    @Inject
    EpaConfig epaConfig;

    protected abstract <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception;

    private void runWithDocker(DockerAction action) throws Exception {
        Utils.runWithDocker(AUTHORIZATION_SERVICE, action);
    }

    private String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
    }

    @Test
    public void getAuthNonceWorks() throws Exception {
        runWithDocker(() -> {
            VauClient vauClient = new VauClient(new VauClientStateMachine());
            String authorizationServiceUrl = epaConfig.getAuthorizationServiceUrl();
            String backend = epaConfig.getEpaBackends().iterator().next();
            String backendUrl = getBackendUrl(backend, authorizationServiceUrl);
            AuthorizationSmcBApi api = buildApi(vauClient, AuthorizationSmcBApi.class, backendUrl);

            for (int i = 0; i < 10; i++) {
                GetNonce200Response nonce = api.getNonce(xUseragent);
                assertNotNull(nonce);
                try (Response response = api.sendAuthorizationRequestSCWithResponse(xUseragent)) {
                    String query = response.getLocation().getQuery();
                    assertTrue(query.contains("redirect_uri"));
                }
            }
        });
    }
}