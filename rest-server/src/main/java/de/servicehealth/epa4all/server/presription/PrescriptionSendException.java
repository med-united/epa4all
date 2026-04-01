package de.servicehealth.epa4all.server.presription;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

import java.io.Serial;

@Getter
public class PrescriptionSendException extends Exception {

    @Serial
    private static final long serialVersionUID = -199407320040723772L;

    private final Response.Status responseStatus;

    public PrescriptionSendException(String message, Response.Status responseStatus) {
        super(message);
        this.responseStatus = responseStatus;
    }
}
