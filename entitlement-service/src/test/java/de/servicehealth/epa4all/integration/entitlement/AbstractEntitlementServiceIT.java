package de.servicehealth.epa4all.integration.entitlement;

import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.api.EntitlementsEPaFdVApi;
import de.servicehealth.api.UserBlockingApi;
import de.servicehealth.epa4all.common.ITAction;
import de.servicehealth.epa4all.common.TestUtils;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.GetEntitlements200Response;
import de.servicehealth.vau.VauFacade;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractEntitlementServiceIT {

    public static final String INFORMATION_SERVICE = "information-service";
    public static final String ENTITLEMENT_SERVICE = "entitlement-service";

    protected String epaUserAgent = "GEMIncenereS2QmFN83P/1.0.0";

    @Inject
    @ConfigProperty(name = "entitlement-service.url")
    String entitlementServiceUrl;

    @Inject
    ClientFactory clientFactory;

    @Inject
    VauFacade vauFacade;

    protected abstract <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception;

    private String getPayload(WebApplicationException e) throws Exception {
        Response response = e.getResponse();
        InputStream inputStream = (InputStream) response.getEntity();
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void runWithDocker(ITAction action) throws Exception {
        TestUtils.runWithDockerContainers(Set.of(INFORMATION_SERVICE, ENTITLEMENT_SERVICE), action);
    }

    @Test
    public void setEntitlementPsWorks() throws Exception {
        runWithDocker(() -> {
            EntitlementsApi api = buildApi(vauFacade, EntitlementsApi.class, entitlementServiceUrl);

            String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlEeXpDQ0EzS2dBd0lCQWdJSEFRMnAvOXp2SXpBS0JnZ3Foa2pPUFFRREFqQ0JtVEVMTUFrR0ExVUVCaE1DUkVVeEh6QWRCZ05WQkFvTUZtZGxiV0YwYVdzZ1IyMWlTQ0JPVDFRdFZrRk1TVVF4U0RCR0JnTlZCQXNNUDBsdWMzUnBkSFYwYVc5dUlHUmxjeUJIWlhOMWJtUm9aV2wwYzNkbGMyVnVjeTFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5MbE5OUTBJdFEwRTVJRlJGVTFRdFQwNU1XVEFlRncweU1EQXhNalF3TURBd01EQmFGdzB5TkRFeU1URXlNelU1TlRsYU1JSGZNUXN3Q1FZRFZRUUdFd0pFUlRFVE1CRUdBMVVFQnd3S1I4TzJkSFJwYm1kbGJqRU9NQXdHQTFVRUVRd0ZNemN3T0RNeEhEQWFCZ05WQkFrTUUwUmhibnBwWjJWeUlGTjBjbUhEbjJVZ01UTXhLakFvQmdOVkJBb01JVE10VTAxRExVSXRWR1Z6ZEd0aGNuUmxMVGc0TXpFeE1EQXdNREV4TmpNMU1qRWRNQnNHQTFVRUJSTVVPREF5TnpZNE9ETXhNVEF3TURBeE1UWXpOVEl4RVRBUEJnTlZCQVFNQ0U1MWJHeHRZWGx5TVE4d0RRWURWUVFxREFaS2RXeHBZVzR4SGpBY0JnTlZCQU1NRlVKaFpDQkJjRzkwYUdWclpWUkZVMVF0VDA1TVdUQmFNQlFHQnlxR1NNNDlBZ0VHQ1Nza0F3TUNDQUVCQndOQ0FBUWU5bmE1VDEyOGNmOGI4VTVkVlYzdGpBQk1QdkttZHIzYVRjRTZwU1ZGdUtGTXJIM3RnYVhoN2pNVHhiOEg3ZVZ5bUtyc2lLUGlJZ2xCK0F2UEFTaXVvNElCV2pDQ0FWWXdEQVlEVlIwVEFRSC9CQUl3QURBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUhBd0l3SHdZRFZSMGpCQmd3Rm9BVVlvaWF4Tjc4by9PVE9jdWZrT2NUbWoySnpIVXdIUVlEVlIwT0JCWUVGQTJZR1B4RTJYcUhlYUZSSURRRDRleXR6d0xGTUE0R0ExVWREd0VCL3dRRUF3SUhnREFnQmdOVkhTQUVHVEFYTUFvR0NDcUNGQUJNQklFak1Ba0dCeXFDRkFCTUJFMHdnWVFHQlNza0NBTURCSHN3ZWFRb01DWXhDekFKQmdOVkJBWVRBa1JGTVJjd0ZRWURWUVFLREE1blpXMWhkR2xySUVKbGNteHBiakJOTUVzd1NUQkhNQmNNRmNPV1ptWmxiblJzYVdOb1pTQkJjRzkwYUdWclpUQUpCZ2NxZ2hRQVRBUTJFeUV6TFZOTlF5MUNMVlJsYzNScllYSjBaUzA0T0RNeE1UQXdNREF4TVRZek5USXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdBMStLWERpWXkyWTBXdkFjUk5URzRmNkNaaVBQSndiWlBrTmJnNUU3ekVVQ0lBYVU0MEFLMmxpVGZMSGkrSjZERCtIVWVLUEdaVGh4OUhwbVFybHJtbjhqIl19.eyJub25jZSI6IjFiYmNhZjkzMWQ2YWU3Y2Y3ODlmYmE0NWI2ZDVhZGViZWFlYTFjYTQ5OTA3NGMyNDAwZGM4Yzc0NjBjMDVkZGUiLCJpYXQiOjE3MjY1NzAxMTEsImV4cCI6MTcyNjU3MDQxMX0.P1z0s8PPUZK_mSVcJ3Sl2bTSzUAEc701DH4R0Vm6YLCP5hs7aDUtgwojDyY3LV0NoziGPgZVQzsOnPRED9qxLw";
            EntitlementRequestType requestType = new EntitlementRequestType().jwt(jwt);

            Exception ex = null;
            try {
                api.setEntitlementPs("Z123456789", "CLIENTID1234567890AB/2.1.12-45", requestType);
            } catch (BadRequestException | ForbiddenException e) {
                ex = e;
                assertTrue(getPayload(e).contains("AUT Certificate is invalid"));
            }
            assertNotNull(ex);
        });
    }

    @Test
    public void deleteBlockedUserPolicyAssignmentWorks() throws Exception {
        runWithDocker(() -> {
            UserBlockingApi api = buildApi(vauFacade, UserBlockingApi.class, entitlementServiceUrl);
            Exception ex = null;
            try {
                api.deleteBlockedUserPolicyAssignment("Z123456789", "2-883110000118994", "CLIENTID1234567890AB/2.1.12-45");
            } catch (NotAllowedException | ForbiddenException e) {
                ex = e;
                String payload = getPayload(e);
                assertTrue(e instanceof NotAllowedException
                    ? payload.contains("Operation not allowed")
                    : payload.contains("Unsupported endpoint")
                );
            }
            assertNotNull(ex);
        });
    }

    @Test
    public void getBlockedUserPolicyAssignmentWorks() throws Exception {
        runWithDocker(() -> {
            UserBlockingApi api = buildApi(vauFacade, UserBlockingApi.class, entitlementServiceUrl);
            Exception ex = null;
            try {
                api.getBlockedUserPolicyAssignment("Z123456789", "2-883110000118994", "CLIENTID1234567890AB/2.1.12-45");
            } catch (NotAllowedException | ForbiddenException e) {
                ex = e;
                String payload = getPayload(e);
                assertTrue(e instanceof NotAllowedException
                    ? payload.contains("Operation not allowed")
                    : payload.contains("Unsupported endpoint")
                );
            }
            assertNotNull(ex);
        });
    }

    @Test
    public void getEntitlementServiceWorks() throws Exception {
        runWithDocker(() -> {
            EntitlementsEPaFdVApi api = buildApi(vauFacade, EntitlementsEPaFdVApi.class, entitlementServiceUrl);
            try {
                GetEntitlements200Response response = api.getEntitlements("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
                assertFalse(response.getData().isEmpty());
            } catch (ForbiddenException e) {
                String payload = getPayload(e);
                assertTrue(payload.contains("Unsupported endpoint"));
            }
        });
    }
}
