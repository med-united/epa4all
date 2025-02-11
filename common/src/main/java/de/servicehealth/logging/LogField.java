package de.servicehealth.logging;

import de.servicehealth.vau.VauClient;
import lombok.Getter;

import java.util.Arrays;

import static de.servicehealth.vau.VauClient.TELEMATIK_ID;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;

/**
 * We have quite some attributes to put in a logging context (MDC) and
 * pass the context from thread to thread or even JMS. Hence, we want to make sure
 * here that we have a proper naming of these attributes along the application
 */
@Getter
public enum LogField {
    VAUNP(VAU_NP),
    BACKEND(X_BACKEND),
    INSURANT(X_INSURANT_ID),
    ICCSN("iccsn"),
    JSON_MESSAGE_TYPE("jsonMessageType"),
    REMOTE_ADDR("remoteAddress"),
    KONNEKTOR("konnektor"),
    TELEMATIKID(TELEMATIK_ID),
    EGK_HANDLE("egkHandle"),
    SMCB_HANDLE("smcbHandle"),
    REQUEST_CORRELATION_ID("requestCorrelationId"),
    CT_ID("ctid"),
    SLOT("slot"),
    WORKPLACE("workPlaceId");

    private final String identifier;

    LogField(String identifier) {
        this.identifier = identifier;
    }

    public static LogField from(String value) {
        return Arrays.stream(values())
            .filter(f -> f.identifier.equalsIgnoreCase(value))
            .findFirst()
            .orElse(null);
    }
}