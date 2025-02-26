package de.service.health.api.epa4all.proxy;

import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbInnerVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbOuterVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public abstract class BaseProxyService {

    protected WebClient setup(
        String url,
        VauConfig vauConfig,
        VauFacade vauFacade,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes,
        boolean includeLogging,
        List<Feature> features
    ) throws Exception {
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauFacade, maskedHeaders, maskedAttributes);
        JsonbInnerVauReaderProvider jsonbInnerVauReaderProvider = new JsonbInnerVauReaderProvider();
        JsonbOuterVauReaderProvider jsonbOuterVauReaderProvider = new JsonbOuterVauReaderProvider();
        List<Object> providers = List.of(
            cborWriterProvider, jsonbVauWriterProvider, jsonbInnerVauReaderProvider, jsonbOuterVauReaderProvider
        );

        WebClient webClient = WebClient.create(url, providers, features, null);
        HTTPConduit conduit = (HTTPConduit) webClient.getConfiguration().getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();

        int connectionTimeoutMs = vauConfig.getConnectionTimeoutMs();
        policy.setConnectionTimeout(connectionTimeoutMs);
        policy.setReceiveTimeout(vauConfig.getVauReadTimeoutMs());
        conduit.setClient(policy);

        List<Interceptor<? extends Message>> outInterceptors = includeLogging
            ? List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade))
            : List.of(new CxfVauSetupInterceptor(vauFacade));
        List<Interceptor<? extends Message>> inInterceptors = includeLogging
            ? List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
            : List.of(new CxfVauReadInterceptor(vauFacade));
        ClientFactory.initClient(
            webClient.getConfiguration(),
            connectionTimeoutMs,
            outInterceptors,
            inInterceptors
        );
        return webClient;
    }

    protected String excludeQueryParams(String query, Set<String> excluded) {
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

    protected List<Pair<String, String>> buildContentHeaders(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        } else {
            return List.of(
                Pair.of(CONTENT_TYPE, "application/json; charset=UTF-8"),
                Pair.of(CONTENT_LENGTH, String.valueOf(body.length))
            );
        }
    }
}