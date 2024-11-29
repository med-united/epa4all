package de.servicehealth.epa4all.cxf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FhirRequest {

    private final boolean isGet;
    private final String accept;
    private final String contentType;
    private final byte[] body;
}
