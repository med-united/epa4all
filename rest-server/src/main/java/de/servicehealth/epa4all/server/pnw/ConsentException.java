package de.servicehealth.epa4all.server.pnw;

import lombok.Getter;

import java.io.Serial;

@Getter
public class ConsentException extends Exception {
    @Serial
    private static final long serialVersionUID = 1927322947646197031L;

    private final String kvnr;

    public ConsentException(String kvnr, String message) {
        super(message);
        this.kvnr = kvnr;
    }
}