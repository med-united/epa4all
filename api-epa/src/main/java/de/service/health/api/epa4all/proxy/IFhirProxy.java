package de.service.health.api.epa4all.proxy;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;

public interface IFhirProxy {

    Response forward(
        boolean isGet,
        String fhirPath,
        UriInfo uriInfo,
        HttpHeaders headers,
        byte[] body,
        Map<String, String> xHeaders
    );
}
