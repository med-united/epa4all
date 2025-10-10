package de.servicehealth.epa4all.server.tss;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

import java.io.Serial;

@Getter
public class TssException extends Exception {

    @Serial
    private static final long serialVersionUID = 671287338886126765L;

    private final Response.Status status;

    public TssException(String message, Response.Status status) {
        super(message);
        this.status = status;
    }
}