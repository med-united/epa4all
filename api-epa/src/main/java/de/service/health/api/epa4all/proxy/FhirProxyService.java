package de.service.health.api.epa4all.proxy;

import de.service.health.api.epa4all.EpaConfig;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.model.FhirRequest;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.vau.VauFacade;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

public class FhirProxyService implements IFhirProxy {

    private final WebClient webClient;

    public FhirProxyService(String backend, EpaConfig epaConfig, VauFacade vauFacade) throws Exception {
        String url = getBackendUrl(backend, epaConfig.getMedicationServiceApiUrl());

        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauFacade);
        JsonbVauReaderProvider jsonbVauReaderProvider = new JsonbVauReaderProvider();
        List<Object> providers = List.of(cborWriterProvider, jsonbVauWriterProvider, jsonbVauReaderProvider);

        webClient = WebClient.create(url, providers);

        HTTPConduit conduit = (HTTPConduit) webClient.getConfiguration().getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();

        policy.setConnectionTimeout(120000);
        policy.setReceiveTimeout(120000);
        conduit.setClient(policy);

        ClientFactory.initClient(
            webClient.getConfiguration(),
            List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
        );
    }

    public Response forward(String fhirPath, UriInfo uriInfo, byte[] body, Map<String, Object> runtimeAttributes) {
        FhirRequest fhirRequest = new FhirRequest(body);

        MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        runtimeAttributes.forEach((key, value) -> map.add(key, (String) value));
        webClient.headers(map);

        String query = excludeParams(uriInfo.getRequestUri(), Set.of(X_INSURANT_ID, X_KONNEKTOR));
        webClient
            .replacePath(fhirPath.replace("fhir", ""))
            .replaceQuery(query);

        return webClient.post(fhirRequest);
    }

    public String excludeParams(URI uri, Set<String> excluded) {
        String query = uri.getQuery();
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
