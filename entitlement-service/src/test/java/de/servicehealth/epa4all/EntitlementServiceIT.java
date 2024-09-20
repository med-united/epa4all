package de.servicehealth.epa4all;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.api.EntitlementsEPaFdVApi;
import de.servicehealth.epa4all.common.DevTestProfile;
import de.servicehealth.epa4all.cxf.interceptor.VAUInterceptor;
import de.servicehealth.epa4all.cxf.provider.JSONBOctetProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVAUTransportFactory;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.GetEntitlements200Response;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAllowedException;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static de.servicehealth.epa4all.cxf.utils.TransportUtils.initClient;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class EntitlementServiceIT {

    private static final Logger log = LoggerFactory.getLogger(EntitlementServiceIT.class);
    private static final String ENTITLEMENT_SERVICE = "entitlement-service";

    @Inject
    @ConfigProperty(name = "entitlement-service.url")
    String entitlementServiceUrl;

    private VauClientStateMachine initVauTransport() {
        Bus bus = BusFactory.getThreadDefaultBus();
        bus.setProperty("force.urlconnection.http.conduit", false);
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        HTTPVAUTransportFactory customTransport = new HTTPVAUTransportFactory();
        dfm.registerDestinationFactory(HTTPVAUTransportFactory.TRANSPORT_IDENTIFIER, customTransport);

        ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
        extension.registerConduitInitiator(HTTPVAUTransportFactory.TRANSPORT_IDENTIFIER, customTransport);

        return new VauClientStateMachine();
    }

    @Test
    public void postEntitlementServiceWorks() throws Exception {
        if (isDockerServiceRunning(ENTITLEMENT_SERVICE)) {
            VauClientStateMachine vauClient = initVauTransport();
            EntitlementsApi api = JAXRSClientFactory.create(
                entitlementServiceUrl, EntitlementsApi.class, getProviders(vauClient)
            );
            initClient(WebClient.client(api), List.of(new VAUInterceptor(vauClient)));

            String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlEeXpDQ0EzS2dBd0lCQWdJSEFRMnAvOXp2SXpBS0JnZ3Foa2pPUFFRREFqQ0JtVEVMTUFrR0ExVUVCaE1DUkVVeEh6QWRCZ05WQkFvTUZtZGxiV0YwYVdzZ1IyMWlTQ0JPVDFRdFZrRk1TVVF4U0RCR0JnTlZCQXNNUDBsdWMzUnBkSFYwYVc5dUlHUmxjeUJIWlhOMWJtUm9aV2wwYzNkbGMyVnVjeTFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5MbE5OUTBJdFEwRTVJRlJGVTFRdFQwNU1XVEFlRncweU1EQXhNalF3TURBd01EQmFGdzB5TkRFeU1URXlNelU1TlRsYU1JSGZNUXN3Q1FZRFZRUUdFd0pFUlRFVE1CRUdBMVVFQnd3S1I4TzJkSFJwYm1kbGJqRU9NQXdHQTFVRUVRd0ZNemN3T0RNeEhEQWFCZ05WQkFrTUUwUmhibnBwWjJWeUlGTjBjbUhEbjJVZ01UTXhLakFvQmdOVkJBb01JVE10VTAxRExVSXRWR1Z6ZEd0aGNuUmxMVGc0TXpFeE1EQXdNREV4TmpNMU1qRWRNQnNHQTFVRUJSTVVPREF5TnpZNE9ETXhNVEF3TURBeE1UWXpOVEl4RVRBUEJnTlZCQVFNQ0U1MWJHeHRZWGx5TVE4d0RRWURWUVFxREFaS2RXeHBZVzR4SGpBY0JnTlZCQU1NRlVKaFpDQkJjRzkwYUdWclpWUkZVMVF0VDA1TVdUQmFNQlFHQnlxR1NNNDlBZ0VHQ1Nza0F3TUNDQUVCQndOQ0FBUWU5bmE1VDEyOGNmOGI4VTVkVlYzdGpBQk1QdkttZHIzYVRjRTZwU1ZGdUtGTXJIM3RnYVhoN2pNVHhiOEg3ZVZ5bUtyc2lLUGlJZ2xCK0F2UEFTaXVvNElCV2pDQ0FWWXdEQVlEVlIwVEFRSC9CQUl3QURBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUhBd0l3SHdZRFZSMGpCQmd3Rm9BVVlvaWF4Tjc4by9PVE9jdWZrT2NUbWoySnpIVXdIUVlEVlIwT0JCWUVGQTJZR1B4RTJYcUhlYUZSSURRRDRleXR6d0xGTUE0R0ExVWREd0VCL3dRRUF3SUhnREFnQmdOVkhTQUVHVEFYTUFvR0NDcUNGQUJNQklFak1Ba0dCeXFDRkFCTUJFMHdnWVFHQlNza0NBTURCSHN3ZWFRb01DWXhDekFKQmdOVkJBWVRBa1JGTVJjd0ZRWURWUVFLREE1blpXMWhkR2xySUVKbGNteHBiakJOTUVzd1NUQkhNQmNNRmNPV1ptWmxiblJzYVdOb1pTQkJjRzkwYUdWclpUQUpCZ2NxZ2hRQVRBUTJFeUV6TFZOTlF5MUNMVlJsYzNScllYSjBaUzA0T0RNeE1UQXdNREF4TVRZek5USXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdBMStLWERpWXkyWTBXdkFjUk5URzRmNkNaaVBQSndiWlBrTmJnNUU3ekVVQ0lBYVU0MEFLMmxpVGZMSGkrSjZERCtIVWVLUEdaVGh4OUhwbVFybHJtbjhqIl19.eyJub25jZSI6IjFiYmNhZjkzMWQ2YWU3Y2Y3ODlmYmE0NWI2ZDVhZGViZWFlYTFjYTQ5OTA3NGMyNDAwZGM4Yzc0NjBjMDVkZGUiLCJpYXQiOjE3MjY1NzAxMTEsImV4cCI6MTcyNjU3MDQxMX0.P1z0s8PPUZK_mSVcJ3Sl2bTSzUAEc701DH4R0Vm6YLCP5hs7aDUtgwojDyY3LV0NoziGPgZVQzsOnPRED9qxLw";
            EntitlementRequestType requestType = new EntitlementRequestType().jwt(jwt);

            // "errorDetail" : "Invalid JWT Payload: Required claims missing from the header (expected are: iat, exp, auditEvidence)"
            assertThrows(
                ForbiddenException.class,
                () -> api.setEntitlementPs("Z123456789", "CLIENTID1234567890AB/2.1.12-45", requestType)
            );
        } else {
            log.warn("Docker container for entitlement-service is not running, skipping a test");
        }
    }

    private static List<JSONBOctetProvider> getProviders(VauClientStateMachine vauClient) {
        JSONBOctetProvider provider = new JSONBOctetProvider(vauClient);
        List<JSONBOctetProvider> providers = new ArrayList<>();
        providers.add(provider);
        return providers;
    }

    @Test
    public void getEntitlementServiceWorks() throws Exception {
        if (isDockerServiceRunning(ENTITLEMENT_SERVICE)) {
            VauClientStateMachine vauClient = initVauTransport();
            EntitlementsEPaFdVApi api = JAXRSClientFactory.create(
                entitlementServiceUrl, EntitlementsEPaFdVApi.class, getProviders(vauClient)
            );
            initClient(WebClient.client(api), List.of(new VAUInterceptor(vauClient)));

            // TODO check endpoint
            
            assertThrows(NotAllowedException.class, () -> {
                GetEntitlements200Response response = api.getEntitlements("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
                assertNotNull(response);
            });
        }
    }
}
