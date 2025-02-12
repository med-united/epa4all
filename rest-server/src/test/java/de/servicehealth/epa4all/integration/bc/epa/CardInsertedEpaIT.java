package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.integration.precondition.NoVauNpPrecondition;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.yasson.internal.JsonBindingBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.runWithEpaBackends;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"unused", "unchecked"})
@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
@QuarkusTestResource(NoVauNpPrecondition.class)
public class CardInsertedEpaIT extends AbstractVsdTest {

    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();

    private final JsonbBuilder jsonbBuilder = new JsonBindingBuilder();

    private final String kvnr = "X110548258";

    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

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

        @OnError
        public void onError(Session session, Throwable throwable) {
            assertNull(throwable);
        }
    }

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
        Set<String> epaBackends = epaConfig.getEpaBackends();
        runWithEpaBackends(epaBackends, () -> {
            try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {

                String telematikId = "1-SMC-B-Testkarte--883110000162363";

                assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
                assertEquals("[" + telematikId + "] SESSION is created", MESSAGES.poll(10, TimeUnit.SECONDS));

                String ctId = "cardTerminal-124";
                String egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);
                CardlinkClient cardlinkClient = mock(CardlinkClient.class);

                String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
                KonnektorConfig konnektorConfig = konnektorConfigs.values().iterator().next();
                String konnektorHost = konnektorConfig.getHost();
                String workplaceId = konnektorConfig.getUserConfigurations().getWorkplaceId();
                String epaBackend = epaBackends.iterator().next();

                vauNpProvider.invalidate();

                epaBackends.forEach(backend -> {
                    Optional<String> vauNpOpt = vauNpProvider.getVauNp(smcbHandle, konnektorHost, workplaceId, epaBackend);
                    assertFalse(vauNpOpt.isPresent());
                });

                // epa-deployment doesn't work for some reason:
                // {"MessageType":"Error","ErrorMessage":"Transcript Error: 500 : [no body]","ErrorCode":5}
                // but epa-as-2.dev.epa4all.de:443 works
                receiveCardInsertedEvent(
                    konnektorConfig,
                    vauNpProvider,
                    cardlinkClient,
                    egkHandle,
                    ctId
                );

                Instant entitlement = new EntitlementFile(new File(configFolder), kvnr).getEntitlement();
                assertNotNull(entitlement);

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
                try (Jsonb build = jsonbBuilder.build()) {
                    WebSocketPayload webSocketPayload = build.fromJson(msg, WebSocketPayload.class);
                    assertEquals(kvnr, webSocketPayload.getKvnr());
                    assertEquals(ctId, webSocketPayload.getCardTerminalId());
                    assertNotNull(webSocketPayload.getMedicationPdfBase64());
                }
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
