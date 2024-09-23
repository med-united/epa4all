package de.servicehealth.epa4all.cxf.utils;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.ConnectionType;

import javax.net.ssl.SSLContext;
import java.security.SecureRandom;
import java.util.Collection;

public class TransportUtils {

    public static SSLContext createFakeSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, FakeTrustManager.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    public static void initClient(Client client, Collection<PhaseInterceptor<Message>> interceptors) throws Exception {
        ClientConfiguration config = WebClient.getConfig(client);
        config.getOutInterceptors().addAll(interceptors);

        HTTPConduit conduit = (HTTPConduit) config.getConduit();
        conduit.getClient().setVersion("1.1");

        conduit.getClient().setAutoRedirect(false);
        conduit.getClient().setAllowChunking(false);
        conduit.getClient().setConnection(ConnectionType.KEEP_ALIVE);

        TLSClientParameters tlsParams = conduit.getTlsClientParameters();
        if (tlsParams == null) {
            tlsParams = new TLSClientParameters();
            conduit.setTlsClientParameters(tlsParams);
        }

        tlsParams.setSslContext(createFakeSSLContext());

        // should not be set to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        // tlsParams.setDisableCNCheck(true);
        // tlsParams.setHostnameVerifier((hostname, session) -> true);

        config.getOutInterceptors().add(new LoggingOutInterceptor());
        config.getInInterceptors().add(new LoggingInInterceptor());
    }
}
