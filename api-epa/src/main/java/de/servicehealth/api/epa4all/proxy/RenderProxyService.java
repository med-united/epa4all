package de.servicehealth.api.epa4all.proxy;

import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.api.epa4all.jmx.EpaMXBeanRegistry;
import de.servicehealth.epa4all.cxf.model.FhirResponse;
import de.servicehealth.epa4all.cxf.model.ForwardRequest;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.jaxrs.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.UPGRADE;
import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

public class RenderProxyService extends BaseProxyService {

    private static final String PDF = "pdf";
    private static final String XHTML = "xhtml";

    private final String backend;
    private final WebClient empClient;
    private final WebClient emlClient;
    private final EpaMXBeanRegistry epaMXBeanRegistry;

    public RenderProxyService(
        String backend,
        EpaConfig epaConfig,
        VauConfig vauConfig,
        VauFacade vauFacade,
        EpaMXBeanRegistry epaMXBeanRegistry,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes,
        List<Feature> features
    ) throws Exception {
        this.backend = backend;
        this.epaMXBeanRegistry = epaMXBeanRegistry;

        String empUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderEmpUrl());
        String emlUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderEmlUrl());
        empClient = setup(empUrl, vauConfig, vauFacade, maskedHeaders, maskedAttributes, true, features);
        emlClient = setup(emlUrl, vauConfig, vauFacade, maskedHeaders, maskedAttributes, true, features);
    }

    public Response getEmpPdf(Map<String, String> xHeaders) {
        return render(empClient, PDF, xHeaders);
    }

    public Response getEmlPdf(Map<String, String> xHeaders) {
        return render(emlClient, PDF, xHeaders);
    }

    public Response getEmlXhtml(Map<String, String> xHeaders) {
        return render(emlClient, XHTML, xHeaders);
    }

    private Response render(WebClient client, String format, Map<String, String> xHeaders) {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>(xHeaders.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        map.add(CONNECTION, "Upgrade, HTTP2-Settings");
        map.add(UPGRADE, "h2c");

        String acceptType = PDF.equals(format) ? APPLICATION_PDF : TEXT_HTML;
        List<Pair<String, String>> acceptHeaders = List.of(Pair.of(ACCEPT, acceptType));
        ForwardRequest forwardRequest = new ForwardRequest(true, acceptHeaders, List.of(), null);

        epaMXBeanRegistry.registerRequest(backend);
        FhirResponse response = client
            .headers(map)
            .replacePath("/" + format)
            .post(forwardRequest, FhirResponse.class);

        Response.ResponseBuilder builder = Response.status(response.getStatus()).entity(response.getPayload());
        response.getHeaders().forEach(p -> builder.header(p.getKey(), p.getValue()));
        builder.type(PDF.equals(format) ? APPLICATION_PDF : TEXT_HTML);
        return builder.build();
    }
}
