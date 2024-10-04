package de.servicehealth.epa4all.cxf.client;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.cxf.interceptor.CxfHeadersInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbWriterProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.ConnectionType;

import java.util.Collection;
import java.util.List;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;

public class ClientFactory {

    private ClientFactory() {
    }

    public static <T> T createPlainClient(Class<T> clazz, String url) throws Exception {
        List<Object> providers = List.of(new JsonbReaderProvider(), new JsonbWriterProvider());
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(WebClient.client(api), List.of(new CxfHeadersInterceptor()));
        return api;
    }

    public static <T> T createProxyClient(Class<T> clazz, String url) throws Exception {
        VauClientStateMachine vauClient = initVauTransport();
        
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauClient);

        T api = JAXRSClientFactory.create(url, clazz, List.of(cborWriterProvider, jsonbVauWriterProvider));
        initClient(WebClient.client(api), List.of(new CxfVauInterceptor(vauClient)));
        return api;
    }

    private static VauClientStateMachine initVauTransport() {
        Bus bus = BusFactory.getThreadDefaultBus();
        bus.setProperty("force.urlconnection.http.conduit", false);
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        HTTPVauTransportFactory customTransport = new HTTPVauTransportFactory();
        dfm.registerDestinationFactory(HTTPVauTransportFactory.TRANSPORT_IDENTIFIER, customTransport);

        ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
        extension.registerConduitInitiator(HTTPVauTransportFactory.TRANSPORT_IDENTIFIER, customTransport);

        // TODO vau pool
        return new VauClientStateMachine();
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
