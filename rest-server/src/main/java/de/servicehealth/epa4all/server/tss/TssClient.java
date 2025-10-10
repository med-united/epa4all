package de.servicehealth.epa4all.server.tss;

import de.servicehealth.epa4all.server.idp.TssConfig;
import de.servicehealth.utils.SSLUtils;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@ApplicationScoped
@Startup
public class TssClient {

    public static final String APPLICATION_FHIR_XML = "application/fhir+xml";

    @Inject
    TssConfig tssConfig;

    HttpClient client;

    @PostConstruct
    public void init() throws Exception {
        SSLParameters sp = new SSLParameters();
        sp.setEndpointIdentificationAlgorithm(null);

        client = HttpClient.newBuilder()
            .sslContext(SSLUtils.createFakeSSLContext())
            .sslParameters(sp)
            .build();
    }

    public HttpResponse<String> submit(String accessToken, byte[] payload) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .header(AUTHORIZATION, "Bearer " + accessToken)
            .header(ACCEPT, APPLICATION_FHIR_XML)
            .header(CONTENT_TYPE, APPLICATION_FHIR_XML)
            .uri(URI.create(tssConfig.getPostUrl()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build();

        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }
}