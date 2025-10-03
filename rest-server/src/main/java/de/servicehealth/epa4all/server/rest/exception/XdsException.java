package de.servicehealth.epa4all.server.rest.exception;

import lombok.Getter;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.Serial;

@Getter
public class XdsException extends Exception {

    @Serial
    private static final long serialVersionUID = -8319288250604497765L;
    
    private final RegistryResponseType registryResponse;

    public XdsException(String message, RegistryResponseType registryResponse) {
        super(message);
        this.registryResponse = registryResponse;
    }
}
