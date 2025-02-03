package de.servicehealth.epa4all.server.logging;

import lombok.Getter;

/**
 * We have quite some attributes to put in a logging context (MDC) and
 * pass the context from thread to thread or even JMS. Hence we want to make sure
 * here that we have a proper naming of these attributes along the application
 */
@Getter
public enum LogContextConstant {
    // Note for changes: Keep them in sync with ere-ps-app for better log-correlations
    SESSION_ID("sessionId"),
    ICCSN("iccsn"),
    PROTOCOL("protocol"),
    JSON_MESSAGE_TYPE("jsonMessageType"),
    REMOTE_ADDR("remoteAddress"),
    WEBSOCKET_CONNECTION_ID("websocketConnectionId"),
    CONNECTION_KONNEKTOR("connectionKonnektor"),;

    private final String identifier;

    LogContextConstant(String identifier) {
        this.identifier = identifier;
    }
}