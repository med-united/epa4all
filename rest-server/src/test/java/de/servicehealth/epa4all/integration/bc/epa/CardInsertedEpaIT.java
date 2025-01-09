package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.runWithEpaBackends;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class CardInsertedEpaIT extends AbstractVsdTest {

    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();
    
    private final String kvnr = "X110485291";

    @TestHTTPResource("/ws/1-SMC-B-Testkarte--883110000162363")
    URI uri;

    @ClientEndpoint
    public static class Client {

        @OnOpen
        public void open(Session session) {
            MESSAGES.add("CONNECT");
            session.getAsyncRemote().sendText("ready");
        }

        @OnMessage
        void message(String msg) {
            MESSAGES.add(msg);
        }
    }

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
        Set<String> epaBackends = epaConfig.getEpaBackends();
        runWithEpaBackends(epaBackends, () -> {
            try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
                assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
                assertEquals("[1-SMC-B-Testkarte--883110000162363] SESSION is created", MESSAGES.poll(10, TimeUnit.SECONDS));

                List<String> statuses = vauNpProvider.reload(epaBackends);
                assertTrue(statuses.getFirst().contains("OK"));

                String egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);
                CardlinkClient cardlinkClient = mock(CardlinkClient.class);

                // epa-deployment doesn't work for some reason:
                // {"MessageType":"Error","ErrorMessage":"Transcript Error: 500 : [no body]","ErrorCode":5}
                // but epa-as-2.dev.epa4all.de:443 works
                receiveCardInsertedEvent(
                    konnektorConfigs.values().iterator().next(),
                    vauNpProvider,
                    cardlinkClient,
                    egkHandle
                );

                ArgumentCaptor<String> messageTypeCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
                verify(cardlinkClient, times(1)).sendJson(any(), any(), messageTypeCaptor.capture(), mapCaptor.capture());

                List<String> capturedMessages = messageTypeCaptor.getAllValues();
                assertTrue(capturedMessages.getFirst().contains("eRezeptBundlesFromAVS"));

                List<Map<String, Object>> maps = mapCaptor.getAllValues();
                Map<String, Object> payloadData = maps.getFirst();

                String bas64EncodedPdfContent = (String) payloadData.get("bundles");
                assertFalse(bas64EncodedPdfContent.isEmpty());
                assertTrue(bas64EncodedPdfContent.startsWith("PDF:"));

                String msg = MESSAGES.poll(20, TimeUnit.SECONDS);
                assertNotNull(msg);
                assertTrue(msg.contains("RetrieveDocumentSetResponseType"));
                assertTrue(msg.contains(kvnr));
            }
        });
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
    }
}
