package de.servicehealth.epa4all.cxf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Getter
@AllArgsConstructor
public class FhirResponse {
    private final List<Pair<String, String>> headers;
    private final byte[] payload;
    private final int status;
}
