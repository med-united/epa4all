package de.servicehealth.epa4all.server.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
@ServerEndpoint(value = "/ws/cetp", encoders = {JsonEncoder.class})
@ApplicationScoped
public class CETPWebsocket {

    private static final Logger log = LoggerFactory.getLogger(TelematikWebsocket.class.getName());

    Map<Session, Boolean> sessions = new ConcurrentHashMap<>();

    public void onTransfer(@ObservesAsync CETPPayload cetpPayload) {
        sendMessage(cetpPayload);
    }

    private <T> void sendMessage(T message) {
        sessions.keySet().forEach(session -> session.getAsyncRemote().sendObject(message, result -> {
            if (result.getException() != null) {
                log.error("Unable to send CETP payload", result.getException());
            }
        }));
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session, true);
        sendMessage("CETP SESSION is created");
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session);
        sendMessage("CETP SESSION error: " + throwable.getMessage());
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("CETP SESSION message: " + message);
    }
}