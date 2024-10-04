package de.servicehealth.epa4all.auth;

import de.servicehealth.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.DockerAction;
import de.servicehealth.epa4all.common.Utils;
import de.servicehealth.model.GetNonce200Response;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractAuthTest {

    public static final String AUTHORIZATION_SERVICE = "authorization-service";

    @Inject
    @ConfigProperty(name = "authorization-service.url")
    String authorizationServiceUrl;

    protected abstract <T> T buildApi(Class<T> clazz, String url) throws Exception;

    private void runWithDocker(DockerAction action) throws Exception {
        Utils.runWithDocker(AUTHORIZATION_SERVICE, action);
    }

    @Test
    public void setEntitlementPsWorks() throws Exception {
        runWithDocker(() -> {
            AuthorizationSmcBApi api = buildApi(AuthorizationSmcBApi.class, authorizationServiceUrl);

            String xUseragent = "CLIENTID1234567890AB/2.1.12-45";
            GetNonce200Response nonce = api.getNonce(xUseragent);
            assertNotNull(nonce);

            try (Response response = api.sendAuthorizationRequestSCWithResponse(xUseragent)) {
                String query = response.getLocation().getQuery();
                assertTrue(query.contains("redirect_uri"));
            }
        });
    }
}
