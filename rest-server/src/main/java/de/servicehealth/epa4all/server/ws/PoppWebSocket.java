package de.servicehealth.epa4all.server.ws;

import de.servicehealth.epa4all.server.ws.payload.WsPoppPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.servicehealth.vau.VauClient.CARD_TERMINAL_ID;

@ServerEndpoint(value = "/ws/ct/{cardTerminalId}", encoders = {JsonEncoder.class})
@ApplicationScoped
public class PoppWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TelematikWebsocket.class.getName());

    Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void onTransfer(@ObservesAsync WsPoppPayload wsPoppPayload) {
        String cardTerminalId = wsPoppPayload.getCardTerminalId();
        Session session = sessions.get(cardTerminalId);
        sendMessage(session, cardTerminalId, wsPoppPayload);
    }

    private <T> void sendMessage(Session session, String cardTerminalId, T message) {
        if (session != null) {
            session.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    String msg = String.format("[%s] Unable to send WS message", cardTerminalId);
                    log.error(msg, result.getException());
                }
            });
        }
    }

    @OnOpen
    public void onOpen(Session session, @PathParam(CARD_TERMINAL_ID) String cardTerminalId) {
        sessions.put(cardTerminalId, session);
        sendMessage(session, cardTerminalId, String.format("[%s] SESSION is created", cardTerminalId));
    }

    @OnClose
    public void onClose(Session session, @PathParam(CARD_TERMINAL_ID) String cardTerminalId) {
        sessions.remove(cardTerminalId);
    }

    @OnError
    public void onError(Session session, @PathParam(CARD_TERMINAL_ID) String cardTerminalId, Throwable throwable) {
        sessions.remove(cardTerminalId);
        String msg = String.format("[%s] SESSION error: %s", cardTerminalId, throwable.getMessage());
        sendMessage(session, cardTerminalId, msg);
    }

    @OnMessage
    public void onMessage(String message, @PathParam(CARD_TERMINAL_ID) String cardTerminalId) {
        Session session = sessions.get(cardTerminalId);
        // sendMessage(session, kvnr, telematikId, "epa4all ready");
    }
}
