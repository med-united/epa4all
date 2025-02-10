package de.servicehealth.logging;

import lombok.Getter;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;

/**
 * We have quite some attributes to put in a logging context (MDC) and
 * pass the context from thread to thread or even JMS. Hence, we want to make sure
 * here that we have a proper naming of these attributes along the application
 */
@Getter
public enum LogField {
    // Note for changes: Keep them in sync with ere-ps-app for better log-correlations
    VAU_SESSION("vauSession"),
    EPA_BACKEND("ePA backend"),
    INSURANT(X_INSURANT_ID),
    ICCSN("iccsn"),
    JSON_MESSAGE_TYPE("jsonMessageType"),
    REMOTE_ADDR("remoteAddress"),
    KONNEKTOR("konnektor"),
    WORKPLACE("workPlaceId");

    private final String identifier;

    LogField(String identifier) {
        this.identifier = identifier;
    }
}