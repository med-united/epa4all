package de.servicehealth.epa4all.server.ws;

import de.servicehealth.epa4all.server.filetracker.download.FileDownloaded;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.TELEMATIK_ID;

@SuppressWarnings({"resource", "unused"})
@ServerEndpoint(value = "/ws/{telematikId}/{kvnr}", encoders = {XmlEncoder.class})
@ApplicationScoped
public class KvnrWebsocket {

    private static final Logger log = Logger.getLogger(KvnrWebsocket.class.getName());

    Map<WsKey, Session> sessions = new ConcurrentHashMap<>();

    public void onTransfer(@ObservesAsync FileDownloaded fileDownloaded) {
        String kvnr = fileDownloaded.getKvnr();
        String telematikId = fileDownloaded.getTelematikId();
        Session session = sessions.get(new WsKey(kvnr, telematikId));
        sendMessage(session, kvnr, telematikId, fileDownloaded.getResponse());
    }

    private <T> void sendMessage(Session session, String kvnr, String telematikId, T message) {
        if (session != null) {
            session.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    String msg = String.format("[%s/%s] Unable to send WS message", telematikId, kvnr);
                    log.log(Level.SEVERE, msg, result.getException());
                }
            });
        }
    }

    @OnOpen
    public void onOpen(Session session, @PathParam(TELEMATIK_ID) String telematikId, @PathParam(KVNR) String kvnr) {
        sessions.put(new WsKey(kvnr, telematikId), session);
        sendMessage(session, kvnr, telematikId, String.format("[%s] SESSION is created", kvnr));
    }

    @OnClose
    public void onClose(Session session, @PathParam(TELEMATIK_ID) String telematikId, @PathParam(KVNR) String kvnr) {
        sessions.remove(new WsKey(kvnr, telematikId));
    }

    @OnError
    public void onError(
        Session session,
        @PathParam(TELEMATIK_ID) String telematikId,
        @PathParam(KVNR) String kvnr,
        Throwable throwable
    ) {
        sessions.remove(new WsKey(kvnr, telematikId));
        String msg = String.format("[%s] SESSION error: %s", kvnr, throwable.getMessage());
        sendMessage(session, kvnr, telematikId, msg);
    }

    @OnMessage
    public void onMessage(String message, @PathParam(TELEMATIK_ID) String telematikId, @PathParam(KVNR) String kvnr) {
        Session session = sessions.get(new WsKey(kvnr, telematikId));
        // sendMessage(session, kvnr, telematikId, "epa4all ready");
    }
}
