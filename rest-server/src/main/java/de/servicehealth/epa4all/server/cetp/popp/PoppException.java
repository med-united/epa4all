package de.servicehealth.epa4all.server.cetp.popp;

import lombok.Getter;

import java.io.Serial;

@Getter
public class PoppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public PoppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
