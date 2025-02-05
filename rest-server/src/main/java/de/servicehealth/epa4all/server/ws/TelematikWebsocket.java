package de.servicehealth.epa4all.server.ws;

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

import static de.servicehealth.vau.VauClient.TELEMATIK_ID;

@SuppressWarnings({"resource", "unused"})
@ServerEndpoint(value = "/ws/{telematikId}", encoders = {JsonEncoder.class})
@ApplicationScoped
public class TelematikWebsocket {

    private static final Logger log = LoggerFactory.getLogger(TelematikWebsocket.class.getName());

    Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void onTransfer(@ObservesAsync WebSocketPayload webSocketPayload) {
        String telematikId = webSocketPayload.getTelematikId();
        Session session = sessions.get(telematikId);
        sendMessage(session, telematikId, webSocketPayload);
    }

    private <T> void sendMessage(Session session, String telematikId, T message) {
        if (session != null) {
            session.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    String msg = String.format("[%s] Unable to send WS message", telematikId);
                    log.error(msg, result.getException());
                }
            });
        }
    }

    @OnOpen
    public void onOpen(Session session, @PathParam(TELEMATIK_ID) String telematikId) {
        sessions.put(telematikId, session);
        sendMessage(session, telematikId, String.format("[%s] SESSION is created", telematikId));
    }

    @OnClose
    public void onClose(Session session, @PathParam(TELEMATIK_ID) String telematikId) {
        sessions.remove(telematikId);
    }

    @OnError
    public void onError(Session session, @PathParam(TELEMATIK_ID) String telematikId, Throwable throwable) {
        sessions.remove(telematikId);
        String msg = String.format("[%s] SESSION error: %s", telematikId, throwable.getMessage());
        sendMessage(session, telematikId, msg);
    }

    @OnMessage
    public void onMessage(String message, @PathParam(TELEMATIK_ID) String telematikId) {
        Session session = sessions.get(telematikId);
        // sendMessage(session, kvnr, telematikId, "epa4all ready");
    }
}
