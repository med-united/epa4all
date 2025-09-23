package de.servicehealth.api.epa4all.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.epa4all.cxf.model.ForwardRequest;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.jaxrs.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.extractJsonNode;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.vau.VauClient.VAU_CLIENT;
import static de.servicehealth.vau.VauClient.VAU_CLIENT_UUID;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT_ENCODING;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public class AdminProxyService extends BaseProxyService implements IAdminProxy {

    private final WebClient adminClient;

    public AdminProxyService(
        String backend,
        EpaConfig epaConfig,
        VauConfig vauConfig,
        VauFacade vauFacade,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes
    ) throws Exception {
        String adminServiceUrl = getBackendUrl(backend, epaConfig.getAdminServiceUrl());
        adminClient = setup(adminServiceUrl, vauConfig, vauFacade, maskedHeaders, maskedAttributes, false, List.of());
    }

    @Override
    public Response forward(
        boolean isGet,
        String adminPath,
        String baseQuery,
        String vauClientUuid,
        HttpHeaders headers,
        Map<String, String> xHeaders,
        byte[] body
    ) {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>(xHeaders.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        String query = excludeQueryParams(baseQuery, Set.of(VAU_CLIENT_UUID, X_BACKEND));
        // String q = query == null || query.isEmpty() ? "" : "?" + query;

        map.add(VAU_CLIENT_UUID, vauClientUuid);

        ForwardRequest forwardRequest = prepareFwdRequest(isGet, body);
        Response response = adminClient
            .headers(map)
            .replacePath(adminPath.replace("vau-admin", ""))
            .replaceQuery(query)
            .post(forwardRequest);

        JsonNode jsonNode = extractJsonNode(response.getEntity());
        if (jsonNode instanceof ObjectNode objectNode) {
            objectNode.put(VAU_CLIENT, vauClientUuid);
            objectNode.put(X_BACKEND, map.getFirst(X_BACKEND));
            objectNode.remove("VAU-Type");
            objectNode.remove("VAU-Version");
            objectNode.remove("KeyID");
        }
        return Response.fromResponse(response).type(APPLICATION_JSON_TYPE).entity(jsonNode).build();
    }

    private ForwardRequest prepareFwdRequest(boolean isGet, byte[] body) {
        List<Pair<String, String>> acceptHeaders = List.of(
            Pair.of(ACCEPT_ENCODING, "gzip"),
            Pair.of(ACCEPT, APPLICATION_JSON)
        );
        List<Pair<String, String>> contentHeaders = buildContentHeaders(body);
        return new ForwardRequest(isGet, acceptHeaders, contentHeaders, body);
    }
}