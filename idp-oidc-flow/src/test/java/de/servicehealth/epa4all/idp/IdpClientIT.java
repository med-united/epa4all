package de.servicehealth.epa4all.idp;

import de.servicehealth.epa4all.config.KonnektorConfig;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import kong.unirest.core.Config;
import kong.unirest.core.HttpRequest;
import kong.unirest.core.HttpRequestSummary;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Interceptor;
import kong.unirest.core.Unirest;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class IdpClientIT {

    @Inject
    IdpClient idpClient;

    @Test
    public void testGetVauNp() throws Exception {
        Unirest.config().interceptor(new Interceptor() {

            @Override
            public void onRequest(HttpRequest<?> request, Config config) {
                System.out.println("Request: " + request);
            }

            @Override
            public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
                System.out.println("Response: " + response);
            }
        });

        idpClient.getVauNp(new KonnektorConfig(), (String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
    }
}