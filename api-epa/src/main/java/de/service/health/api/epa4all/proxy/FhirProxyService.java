package de.service.health.api.epa4all.proxy;

import de.service.health.api.epa4all.EpaConfig;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.model.FhirRequest;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static org.apache.http.HttpHeaders.CONNECTION;
import static org.apache.http.HttpHeaders.UPGRADE;

public class FhirProxyService implements IFhirProxy {

    private final WebClient apiClient;
    private final WebClient renderClient;
    private final String epaUserAgent;

    public FhirProxyService(String backend, EpaConfig epaConfig, VauConfig vauConfig, VauFacade vauFacade) throws Exception {
        String apiUrl = getBackendUrl(backend, epaConfig.getMedicationServiceApiUrl());
        String renderUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderUrl());

        epaUserAgent = epaConfig.getEpaUserAgent();

        apiClient = setup(apiUrl, vauConfig, vauFacade);
        renderClient = setup(renderUrl, vauConfig, vauFacade);
    }

    private WebClient setup(String url, VauConfig vauConfig, VauFacade vauFacade) throws Exception {
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauFacade);
        JsonbVauReaderProvider jsonbVauReaderProvider = new JsonbVauReaderProvider();
        List<Object> providers = List.of(cborWriterProvider, jsonbVauWriterProvider, jsonbVauReaderProvider);

        WebClient webClient = WebClient.create(url, providers);
        HTTPConduit conduit = (HTTPConduit) webClient.getConfiguration().getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();

        policy.setConnectionTimeout(vauConfig.getConnectionTimeoutMs());
        policy.setReceiveTimeout(vauConfig.getRequestTimeoutMs());
        conduit.setClient(policy);

        ClientFactory.initClient(
            webClient.getConfiguration(),
            List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade, epaUserAgent)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
        );
        return webClient;
    }

    public Response forward(
        boolean isGet,
        boolean ui5,
        String fhirPath,
        UriInfo uriInfo,
        HttpHeaders headers,
        byte[] body,
        Map<String, String> xHeaders
    ) {
        boolean isPdf = fhirPath.contains("fhir/pdf");
        boolean isXhtml = fhirPath.contains("fhir/xhtml");
        boolean isRender = isPdf || isXhtml;

        WebClient webClient = isRender ? renderClient : apiClient;
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>(xHeaders.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        if (isRender) {
            map.add(CONNECTION, "Upgrade, HTTP2-Settings");
            map.add(UPGRADE, "h2c");
        }

        String query = excludeQueryParams(uriInfo.getRequestUri().getQuery(), Set.of("subject", X_INSURANT_ID, X_KONNEKTOR));

        String accept = isPdf ? "Accept: application/pdf" : isXhtml ? "Accept: text/html" : "Accept-Encoding: gzip\r\nAccept: application/fhir+json;q=1.0, application/json+fhir;q=0.9";
        String contentType = body == null || body.length == 0 ? "" : "application/fhir+json; charset=UTF-8";
        FhirRequest fhirRequest = new FhirRequest(isGet, accept, contentType, body);

        Response response = webClient
            .headers(map)
            .replacePath(fhirPath.replace("fhir", ""))
            .replaceQuery(query)
            .post(fhirRequest);

        if (isPdf) {
            return Response.fromResponse(response).type("application/pdf").build();
        } else {
            // Add JSON as content type. This is needed for UI5 so it can correctly
            // parse the data
            MediaType type = isXhtml ? MediaType.TEXT_HTML_TYPE
                : ui5 ? MediaType.APPLICATION_JSON_PATCH_JSON_TYPE : MediaType.APPLICATION_JSON_TYPE;
            return Response.fromResponse(response).type(type).build();
        }
    }

    public String excludeQueryParams(String query, Set<String> excluded) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        Map<String, String> params = Arrays.stream(query.split("&"))
            .map(p -> p.split("=", 2))
            .filter(p -> excluded.stream().noneMatch(ex -> ex.equalsIgnoreCase(p[0])))
            .collect(Collectors.toMap(p -> p[0], p -> p.length > 1 ? p[1] : ""));

        return params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    }
}
