package de.servicehealth.epa4all.cxf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FhirRequest {

    private final byte[] body;
}
