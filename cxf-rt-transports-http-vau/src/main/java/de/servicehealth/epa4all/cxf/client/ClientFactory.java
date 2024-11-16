package de.servicehealth.epa4all.cxf.client;

import de.servicehealth.epa4all.cxf.interceptor.CxfHeadersInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbWriterProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static org.apache.cxf.transports.http.configuration.ConnectionType.KEEP_ALIVE;

@ApplicationScoped
public class ClientFactory {

    // PU epa.health/1.0.0 ServiceHealthGmbH/GEMIncenereS2QmFN83P
    public static final String USER_AGENT = "GEMIncenereSud1PErUR/1.0.0";

    void onStart(@Observes StartupEvent ev) {
        initGlobalBus();
    }

    private void initGlobalBus() {
        Bus globalBus = BusFactory.getDefaultBus();
        globalBus.setProperty("force.urlconnection.http.conduit", false);
        DestinationFactoryManager dfm = globalBus.getExtension(DestinationFactoryManager.class);
        HTTPVauTransportFactory customTransport = new HTTPVauTransportFactory();
        dfm.registerDestinationFactory(TRANSPORT_IDENTIFIER, customTransport);

        ConduitInitiatorManager extension = globalBus.getExtension(ConduitInitiatorManager.class);
        extension.registerConduitInitiator(TRANSPORT_IDENTIFIER, customTransport);
    }

    public <T> T createPlainClient(Class<T> clazz, String url) throws Exception {
        List<Object> providers = List.of(new JsonbReaderProvider(), new JsonbWriterProvider());
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(
            WebClient.getConfig(api),
            List.of(new LoggingOutInterceptor(), new CxfHeadersInterceptor()),
            List.of(new LoggingInInterceptor())
        );
        return api;
    }

    public <T> T createProxyClient(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauFacade);
        JsonbVauReaderProvider jsonbVauReaderProvider = new JsonbVauReaderProvider();
        List<Object> providers = List.of(cborWriterProvider, jsonbVauWriterProvider, jsonbVauReaderProvider);
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(
            WebClient.getConfig(api),
            List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
        );
        return api;
    }

    public static void initClient(
        ClientConfiguration config,
        List<Interceptor<? extends Message>> outInterceptors,
        List<Interceptor<? extends Message>> inInterceptors
    ) throws Exception {
        config.getOutInterceptors().addAll(outInterceptors);
        config.getInInterceptors().addAll(inInterceptors);

        HTTPConduit conduit = (HTTPConduit) config.getConduit();
        HTTPClientPolicy client = conduit.getClient();
        client.setVersion("1.1");
        client.setAutoRedirect(false);
        client.setAllowChunking(false);
        client.setConnection(KEEP_ALIVE);

        TLSClientParameters tlsParams = new TLSClientParameters();
        // setDisableCNCheck and setHostnameVerifier should not be set
        // to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        tlsParams.setSslContext(createFakeSSLContext());
        conduit.setTlsClientParameters(tlsParams);
    }
}
