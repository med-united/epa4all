package de.servicehealth.epa4all.integration.base;

import de.service.health.api.epa4all.EpaConfig;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.ITAction;
import de.servicehealth.epa4all.common.TestUtils;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.model.GetNonce200Response;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractAuthIT {

    public static final String AUTHORIZATION_SERVICE = "authorization-service";

    @Inject
    protected IdpConfig idpConfig;

    @Inject
    protected EpaConfig epaConfig;

    @Inject
    protected VauFacade vauFacade;

    @Inject
    protected ClientFactory clientFactory;

    protected abstract <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception;

    private void runWithDocker(ITAction action) throws Exception {
        TestUtils.runWithDockerContainers(Set.of(AUTHORIZATION_SERVICE), action);
    }

    private String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
    }

    @Test
    public void getAuthNonceWorks() throws Exception {
        runWithDocker(() -> {
            String authorizationServiceUrl = epaConfig.getAuthorizationServiceUrl();
            String backend = epaConfig.getEpaBackends().iterator().next();
            String backendUrl = getBackendUrl(backend, authorizationServiceUrl);
            AuthorizationSmcBApi api = buildApi(vauFacade, AuthorizationSmcBApi.class, backendUrl);

            VauClient vauClient = vauFacade.getEmptyClients().getFirst();

            String clientId = idpConfig.getClientId();
            String epaUserAgent = epaConfig.getEpaUserAgent();
            for (int i = 0; i < 10; i++) {
                GetNonce200Response nonce = api.getNonce(epaUserAgent);
                assertNotNull(nonce);
                try (Response response = api.sendAuthorizationRequestSCWithResponse(clientId, epaUserAgent, "test:8080", vauClient.getUuid())) {
                    String query = response.getLocation().getQuery();
                    assertTrue(query.contains("redirect_uri"));
                }
            }
        });
    }
}
