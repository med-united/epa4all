package de.servicehealth.epa4all.server.filetracker;

import lombok.Getter;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

@Getter
public class FileUploadException extends Exception {

    private final RegistryResponseType response;

    public FileUploadException(String message, RegistryResponseType response) {
        super(message);
        this.response = response;
    }
}
