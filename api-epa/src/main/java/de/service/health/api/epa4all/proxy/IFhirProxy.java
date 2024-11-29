package de.service.health.api.epa4all.proxy;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;

public interface IFhirProxy {

    Response forward(boolean isGet, String fhirPath, UriInfo uriInfo, byte[] body, Map<String, Object> xHeaders);
}
