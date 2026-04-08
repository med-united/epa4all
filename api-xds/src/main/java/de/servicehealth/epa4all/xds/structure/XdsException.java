package de.servicehealth.epa4all.xds.structure;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

import java.io.Serial;

@Getter
public class XdsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -8319288250604497765L;

    private final Response.Status status;

    public XdsException(String message, Response.Status status) {
        super(message);
        this.status = status;
    }
}
