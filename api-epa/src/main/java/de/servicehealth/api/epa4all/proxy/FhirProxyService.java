package de.servicehealth.api.epa4all.proxy;

import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.api.epa4all.jmx.EpaMXBeanRegistry;
import de.servicehealth.epa4all.cxf.model.FhirResponse;
import de.servicehealth.epa4all.cxf.model.ForwardRequest;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.UPGRADE;
import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_SUBJECT;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT_ENCODING;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML_TYPE;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

public class FhirProxyService extends BaseProxyService implements IFhirProxy {

    private static final Logger log = LoggerFactory.getLogger(FhirProxyService.class.getName());

    private final String backend;
    private final WebClient apiClient;
    private final WebClient renderClient;
    private final EpaMXBeanRegistry epaMXBeanRegistry;

    public FhirProxyService(
        String backend,
        EpaConfig epaConfig,
        VauConfig vauConfig,
        VauFacade vauFacade,
        EpaMXBeanRegistry epaMXBeanRegistry,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes,
        List<Feature> features
    ) throws Exception {
        String apiUrl = getBackendUrl(backend, epaConfig.getMedicationServiceApiUrl());
        String renderUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderUrl());

        this.backend = backend;
        this.epaMXBeanRegistry = epaMXBeanRegistry;

        apiClient = setup(apiUrl, vauConfig, vauFacade, maskedHeaders, maskedAttributes, true, features);
        renderClient = setup(renderUrl, vauConfig, vauFacade, maskedHeaders, maskedAttributes, true, features);
    }

    public Response forward(
        boolean isGet,
        boolean ui5,
        String fhirPath,
        String baseQuery,
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

        String query = excludeQueryParams(baseQuery, Set.of(X_SUBJECT, X_INSURANT_ID, X_KONNEKTOR, X_WORKPLACE));
        String q = query == null || query.isEmpty() ? "" : "?" + query;
        log.info(String.format("Fhir Forwarding %s%s", fhirPath, q));

        List<Pair<String, String>> acceptHeaders;
        if (isPdf) {
            acceptHeaders = List.of(Pair.of(ACCEPT, APPLICATION_PDF));
        } else if (isXhtml) {
            acceptHeaders = List.of(Pair.of(ACCEPT, TEXT_HTML));
        } else {
            acceptHeaders = List.of(
                Pair.of(ACCEPT_ENCODING, "gzip"),
                Pair.of(ACCEPT, "application/fhir+json;q=1.0, application/json+fhir;q=0.9")
            );
        }
        List<Pair<String, String>> contentHeaders = buildContentHeaders(body);
        ForwardRequest forwardRequest = new ForwardRequest(isGet, acceptHeaders, contentHeaders, body);
        epaMXBeanRegistry.registerRequest(backend);
        FhirResponse response = webClient
            .headers(map)
            .replacePath(fhirPath.replace("fhir", ""))
            .replaceQuery(query)
            .post(forwardRequest, FhirResponse.class);

        Response.ResponseBuilder builder = Response.status(response.getStatus()).entity(response.getPayload());
        response.getHeaders().forEach(p -> builder.header(p.getKey(), p.getValue()));
        if (isPdf) {
            builder.type(APPLICATION_PDF);
        } else if (isXhtml) {
            builder.type(TEXT_HTML_TYPE);
        } else {
            builder.type(ui5 ? APPLICATION_JSON_PATCH_JSON_TYPE : APPLICATION_JSON_TYPE);
        }
        return builder.build();
    }

    @Override
    protected List<Pair<String, String>> buildContentHeaders(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        } else {
            return List.of(
                Pair.of(CONTENT_TYPE, "application/fhir+json; charset=UTF-8"),
                Pair.of(CONTENT_LENGTH, String.valueOf(body.length))
            );
        }
    }
}
