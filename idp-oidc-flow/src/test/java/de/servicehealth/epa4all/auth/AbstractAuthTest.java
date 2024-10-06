package de.servicehealth.epa4all.auth;

import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.DockerAction;
import de.servicehealth.epa4all.common.Utils;
import de.servicehealth.model.GetNonce200Response;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initVauTransport;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractAuthTest {

    public static final String AUTHORIZATION_SERVICE = "authorization-service";

    String xUseragent = "CLIENTID1234567890AB/2.1.12-45";

    @Inject
    @ConfigProperty(name = "authorization-service.url")
    String authorizationServiceUrl;

    protected abstract <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception;

    private void runWithDocker(DockerAction action) throws Exception {
        Utils.runWithDocker(AUTHORIZATION_SERVICE, action);
    }

    @Test
    public void getAuthNonceWorks() throws Exception {
        runWithDocker(() -> {
            VauClient vauClient = new VauClient(initVauTransport());

            AuthorizationSmcBApi api = buildApi(vauClient, AuthorizationSmcBApi.class, authorizationServiceUrl);
            GetNonce200Response nonce = api.getNonce(xUseragent);
            assertNotNull(nonce);

            // AuthorizationSmcBApi api2 = buildApi(vauClient, AuthorizationSmcBApi.class, authorizationServiceUrl);
            try (Response response = api.sendAuthorizationRequestSCWithResponse(xUseragent)) {
                String query = response.getLocation().getQuery();
                assertTrue(query.contains("redirect_uri"));
            }
        });
    }
}
