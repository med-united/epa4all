package de.servicehealth.api.epa4all;

import lombok.Getter;

import java.io.Serial;

@Getter
public class EpaNotFoundException extends Exception {

    @Serial
    private static final long serialVersionUID = 4683832030650224656L;
    
    private final String insurantId;

    public EpaNotFoundException(String message, String insurantId) {
        super(message);
        this.insurantId = insurantId;
    }
}
