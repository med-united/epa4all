package de.service.health.api.epa4all.proxy;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public interface IAdminProxy {

    Response forward(
        boolean isGet,
        String adminPath,
        String baseQuery,
        String vauClientUuid,
        HttpHeaders headers,
        Map<String, String> xHeaders,
        byte[] body
    );

    default Response forwardGet(
        String adminPath,
        String vauClientUuid,
        Map<String, String> xHeaders
    ) {
        return forward(true, adminPath, null, vauClientUuid, null, xHeaders, null);
    }
}