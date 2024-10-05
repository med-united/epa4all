package de.servicehealth.epa4all.cxf.client;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.cxf.interceptor.CxfHeadersInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbWriterProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.ConnectionType;

import java.util.List;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;

public class ClientFactory {

    private ClientFactory() {
    }

    public static <T> T createPlainClient(Class<T> clazz, String url) throws Exception {
        List<Object> providers = List.of(new JsonbReaderProvider(), new JsonbWriterProvider());
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(
            WebClient.getConfig(api),
            List.of(new LoggingOutInterceptor(), new CxfHeadersInterceptor()),
            List.of(new LoggingInInterceptor())
        );
        return api;
    }

    public static <T> T createProxyClient(Class<T> clazz, String url) throws Exception {
        VauClient vauClient = new VauClient(initVauTransport());
        
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauClient);
        JsonbVauReaderProvider jsonbVauReaderProvider = new JsonbVauReaderProvider();
        List<Object> providers = List.of(cborWriterProvider, jsonbVauWriterProvider, jsonbVauReaderProvider);
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(
            WebClient.getConfig(api),
            List.of(new LoggingOutInterceptor(), new CxfVauWriteInterceptor(vauClient)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauClient))
        );
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

        return new VauClientStateMachine();
    }

    public static void initClient(
        ClientConfiguration config,
        List<Interceptor<? extends Message>> outInterceptors,
        List<Interceptor<? extends Message>> inInterceptors
    ) throws Exception {
        config.getOutInterceptors().addAll(outInterceptors);
        config.getInInterceptors().addAll(inInterceptors);

        HTTPConduit conduit = (HTTPConduit) config.getConduit();
        conduit.getClient().setVersion("1.1");

        conduit.getClient().setAutoRedirect(false);
        conduit.getClient().setAllowChunking(false);
        conduit.getClient().setConnection(ConnectionType.KEEP_ALIVE);

        TLSClientParameters tlsParams = new TLSClientParameters();
        // setDisableCNCheck and setHostnameVerifier should not be set
        // to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        tlsParams.setSslContext(createFakeSSLContext());
        conduit.setTlsClientParameters(tlsParams);
    }
}
