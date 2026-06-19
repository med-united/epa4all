package de.servicehealth.epa4all.integration.bc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.servicehealth.epa4all.common.profile.PoppWireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.ws.payload.WsPoppPayload;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
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
import jakarta.websocket.WebSocketContainer;
import org.eclipse.yasson.internal.JsonBindingBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static de.servicehealth.epa4all.common.TestUtils.getTextFixture;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(PoppWireMockProfile.class)
public class CardInsertedPoppIT extends AbstractWiremockTest {

    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();

    private static final String CARD_TERMINAL_ID = "cardTerminal-124";

    private static final String POPP_TOKEN = "eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzc2NTQyOTI5LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3NjU0MjkyOSwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.Uqh-NBl3O0jd-xeTR7N0ZLHGkqHo3XBOT-TVh0q8l3BrkBls6dtceZha-1RC2NOXpTkmnjAbAi8m7dlJUcGC-g";

    private static WireMockServer poppWmServer;

    private final JsonbBuilder jsonbBuilder = new JsonBindingBuilder();

    @InjectMock
    EntitlementService entitlementService;

    @TestHTTPResource("/ws/ct/" + CARD_TERMINAL_ID)
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

    @BeforeAll
    public static void startPoppServer() {
        poppWmServer = new WireMockServer(options().port(PoppWireMockProfile.POPP_WIREMOCK_PORT));
        poppWmServer.start();
    }

    @AfterAll
    public static void stopPoppServer() {
        if (poppWmServer != null) {
            poppWmServer.stop();
        }
    }

    @Test
    public void cardInsertedEmitsPoppWebsocketPayload() throws Exception {
        stubPoppClient(aResponse().withStatus(200).withBody(POPP_TOKEN));
        stubPoppVsdm(aResponse().withStatus(200)
            .withHeader("Content-Type", APPLICATION_XML)
            .withBody(getTextFixture("PoppReadVSDResponse.xml")));

        try (Session session = connectToWebSocket()) {
            String kvnr = "X110587452";
            receiveCardInsertedEvent(null, kvnr);

            // since flow is async we emulate CountDownLatch as timeout(20_000)
            verify(entitlementService, timeout(20_000)).setEntitlementV2(POPP_TOKEN);

            String msg = MESSAGES.poll(20, TimeUnit.SECONDS);
            assertNotNull(msg);
            try (Jsonb build = jsonbBuilder.build()) {
                WsPoppPayload payload = build.fromJson(msg, WsPoppPayload.class);
                assertEquals(CARD_TERMINAL_ID, payload.getCardTerminalId());
                assertNotNull(payload.getTelematikId());
                assertEquals(POPP_TOKEN, payload.getPoppToken());
                assertTrue(payload.getCetpXml().contains("CARD/INSERTED"));
                assertTrue(payload.getReadVSDResponseXml().contains("ReadVSDResponse"));
                assertTrue(payload.getReadVSDResponseXml().contains("PersoenlicheVersichertendaten"));
            }
        }
    }

    @Test
    public void cardInsertedDoesNotEmitPayloadWhenPoppClientFails() throws Exception {
        stubPoppClient(aResponse().withStatus(500).withBody("Popp Client is down"));

        try (Session session = connectToWebSocket()) {
            String kvnr = "X110587452";
            receiveCardInsertedEvent(null, kvnr);

            verify(entitlementService, after(3_000).never()).setEntitlementV2(anyString());
            assertNull(MESSAGES.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cardInsertedDoesNotEmitPayloadWhenVsdmFails() throws Exception {
        stubPoppClient(aResponse().withStatus(200).withBody(POPP_TOKEN));
        stubPoppVsdm(aResponse().withStatus(500).withBody("VSDM is down"));

        try (Session session = connectToWebSocket()) {
            String kvnr = "X110587452";
            receiveCardInsertedEvent(null, kvnr);

            verify(entitlementService, after(3_000).never()).setEntitlementV2(anyString());
            assertNull(MESSAGES.poll(2, TimeUnit.SECONDS));
        }
    }

    private Session connectToWebSocket() throws Exception {
        MESSAGES.clear();
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session session = container.connectToServer(CardInsertedPoppIT.Client.class, uri);
        assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
        assertEquals("[" + CARD_TERMINAL_ID + "] SESSION is created", MESSAGES.poll(10, TimeUnit.SECONDS));
        return session;
    }

    private void stubPoppClient(ResponseDefinitionBuilder response) {
        poppWmServer.resetAll();
        poppWmServer.addStubMapping(post(urlEqualTo(PoppWireMockProfile.POPP_CLIENT_PATH)).willReturn(response).build());
    }

    private void stubPoppVsdm(ResponseDefinitionBuilder response) {
        poppWmServer.addStubMapping(get(urlEqualTo(PoppWireMockProfile.POPP_VSDM_PATH)).willReturn(response).build());
    }
}
