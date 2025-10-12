package de.servicehealth.epa4all.server.tss;

import de.servicehealth.epa4all.server.idp.TssConfig;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import java.net.URI;

import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@ApplicationScoped
@Startup
public class TssClient {

    public static final String APPLICATION_FHIR_XML = "application/fhir+xml";

    @Inject
    TssConfig tssConfig;

    Executor executor;

    @PostConstruct
    public void init() throws Exception {
        CloseableHttpClient httpclient = HttpClients.custom()
            .setSSLHostnameVerifier((h, s) -> true)
            .setSSLContext(createFakeSSLContext())
            .build();
        executor = Executor.newInstance(httpclient);
    }

    public HttpResponse submit(String accessToken, byte[] payload) throws Exception {
        Request request = Request
            .Post(URI.create(tssConfig.getPostUrl()))
            .setHeaders(
                new BasicHeader(AUTHORIZATION, "Bearer " + accessToken),
                new BasicHeader(ACCEPT, APPLICATION_FHIR_XML),
                new BasicHeader(CONTENT_TYPE, APPLICATION_FHIR_XML))
            .bodyByteArray(payload);

        return executor.execute(request).returnResponse();
    }
}