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
        HttpHeaders headers,
        byte[] body,
        Map<String, String> xHeaders
    );

    Response forwardGet(
        String fhirPath,
        Map<String, String> xHeaders
    );
}
