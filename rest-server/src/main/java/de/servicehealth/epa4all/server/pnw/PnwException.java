package de.servicehealth.epa4all.server.pnw;

import lombok.Getter;

import java.io.Serial;

@Getter
public class PnwException extends Exception {

    @Serial
    private static final long serialVersionUID = -5433645221576762281L;

    private final String kvnr;

    public PnwException(String kvnr, String message) {
        super(message);
        this.kvnr = kvnr;
    }
}