package de.service.health.api.epa4all.proxy;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public interface IFhirProxy {

    Response forward(
        boolean isGet,
        boolean ui5,
        String fhirPath,
        String baseQuery,
        String konnektor,
        String workplace,
        HttpHeaders headers,
        byte[] body,
        Map<String, String> xHeaders
    );

    default Response forwardGet(
        String fhirPath,
        String konnektor,
        String workplace,
        Map<String, String> xHeaders
    ) {
        return forward(true, false, fhirPath, null, konnektor, workplace, null, null, xHeaders);
    }
}
