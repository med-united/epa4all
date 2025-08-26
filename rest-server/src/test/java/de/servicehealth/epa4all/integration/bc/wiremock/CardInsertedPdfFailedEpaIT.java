package de.servicehealth.epa4all.integration.bc.wiremock;

import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.ws.CETPPayload;
import io.quarkus.test.InjectMock;
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
import jakarta.websocket.WebSocketContainer;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.yasson.internal.JsonBindingBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class CardInsertedPdfFailedEpaIT extends AbstractWiremockTest {

    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();

    private final JsonbBuilder jsonbBuilder = new JsonBindingBuilder();

    @InjectMock
    FileEventSender fileEventSender;

    @TestHTTPResource("/ws/cetp")
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
    public void medicationPdfWasNotSentToCardlinkBecauseOfNotAuthorizedError() throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try (Session session = container.connectToServer(CardInsertedPdfFailedEpaIT.Client.class, uri)) {
            assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
            assertEquals("CETP SESSION is created", MESSAGES.poll(10, TimeUnit.SECONDS));

            String pdfError = "{\"errorCode\":\"internalError\",\"errorDetail\":\"Requestor not authorized\"}";
            prepareVauStubs(List.of(
                Pair.of("/epa/medication/render/v1/eml/pdf", new CallInfo().withErrorHeader(pdfError))
            ));
            prepareInformationStubs(204);

            String kvnr = "X110587452";
            EpaFileDownloader mockDownloader = mock(EpaFileDownloader.class);
            receiveCardInsertedEvent(mockDownloader, null, kvnr);
            verify(mockDownloader, never()).handleDownloadResponse(any(), any());

            String msg = MESSAGES.poll(20, TimeUnit.SECONDS);
            assertNotNull(msg);
            try (Jsonb build = jsonbBuilder.build()) {
                CETPPayload cetpPayload = build.fromJson(msg, CETPPayload.class);
                assertEquals("X110485291", cetpPayload.getKvnr());
                assertTrue(cetpPayload.getError().contains("Problem with reading the data"));
                assertNotNull(cetpPayload.getParameters());
            }
        }
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
    }
}