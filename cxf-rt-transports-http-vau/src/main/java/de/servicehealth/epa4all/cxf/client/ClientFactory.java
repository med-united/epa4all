package de.servicehealth.epa4all.cxf.client;

import de.servicehealth.epa4all.cxf.interceptor.CxfHeadersInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbInnerVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbOuterVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbPlainReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbWriterProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.management.InstrumentationManager;
import org.apache.cxf.management.counters.CounterRepository;
import org.apache.cxf.management.jmx.InstrumentationManagerImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.List;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;
import static java.lang.Boolean.TRUE;

@ApplicationScoped
public class ClientFactory extends StartableService {

    @Inject
    VauConfig vauConfig;

    @Inject
    @Named("gematikSSLContext")
    SSLContext gematikSslContext;

    @Inject
    X509TrustManager gematikTrustManager;

    @Override
    public int getPriority() {
        return CxfClientFactoryPriority;
    }

    public void doStart() {
        Bus globalBus = BusFactory.getDefaultBus();
        // CXF's HttpClientHTTPConduit (java.net.http based) ignores TLSClientParameters, so the
        // VAU/ePA WebClients fell back to the JVM default SSLContext (cacerts, no TI CAs) and failed
        // PKIX. Force the URLConnection conduit, which honours the per-conduit trust config set in
        // initConduit() (our Gematik trust manager: all TSL CAs + leaf-only TI chain completion).
        // Scoped to this bus's CXF clients, so the IDP client and others are unaffected.
        globalBus.setProperty("force.urlconnection.http.conduit", true);
        globalBus.setProperty("bus.jmx.usePlatformMBeanServer", TRUE);
        globalBus.setProperty("bus.jmx.enabled", TRUE);

        InstrumentationManagerImpl instrumentationManager = new InstrumentationManagerImpl(globalBus);
        instrumentationManager.setEnabled(true);
        instrumentationManager.setUsePlatformMBeanServer(true);
        globalBus.setExtension(instrumentationManager, InstrumentationManager.class);

        CounterRepository counterRepository = new CounterRepository();
        counterRepository.setBus(globalBus);
        globalBus.setExtension(counterRepository, CounterRepository.class);

        instrumentationManager.init();

        DestinationFactoryManager dfm = globalBus.getExtension(DestinationFactoryManager.class);
        HTTPVauTransportFactory customTransport = new HTTPVauTransportFactory(vauConfig);
        dfm.registerDestinationFactory(TRANSPORT_IDENTIFIER, customTransport);

        ConduitInitiatorManager extension = globalBus.getExtension(ConduitInitiatorManager.class);
        extension.registerConduitInitiator(TRANSPORT_IDENTIFIER, customTransport);
    }

    public <T> T createRestPlainClient(Class<T> clazz, String url) throws Exception {
        List<Object> providers = List.of(new JsonbPlainReaderProvider(), new JsonbWriterProvider());
        T api = JAXRSClientFactory.create(url, clazz, providers);
        initClient(
            WebClient.getConfig(api),
            vauConfig.getConnectionTimeoutMs(),
            List.of(new LoggingOutInterceptor(), new CxfHeadersInterceptor()),
            List.of(new LoggingInInterceptor())
        );
        return api;
    }

    public <T> T createRestProxyClient(
        VauFacade vauFacade,
        Class<T> clazz,
        String url,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes,
        List<Feature> features
    ) throws Exception {
        CborWriterProvider cborWriterProvider = new CborWriterProvider();
        JsonbVauWriterProvider jsonbVauWriterProvider = new JsonbVauWriterProvider(vauFacade, maskedHeaders, maskedAttributes);
        JsonbInnerVauReaderProvider jsonbInnerVauReaderProvider = new JsonbInnerVauReaderProvider();
        JsonbOuterVauReaderProvider jsonbOuterVauReaderProvider = new JsonbOuterVauReaderProvider();
        List<Object> providers = List.of(
            cborWriterProvider, jsonbVauWriterProvider, jsonbInnerVauReaderProvider, jsonbOuterVauReaderProvider
        );
        T api = JAXRSClientFactory.create(url, clazz, providers, features, null);
        initClient(
            WebClient.getConfig(api),
            vauConfig.getConnectionTimeoutMs(),
            List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
        );
        return api;
    }

    public void initClient(
        ClientConfiguration config,
        int connectionTimeoutMs,
        List<Interceptor<? extends Message>> outInterceptors,
        List<Interceptor<? extends Message>> inInterceptors
    ) {
        config.getOutInterceptors().addAll(outInterceptors);
        config.getInInterceptors().addAll(inInterceptors);

        initConduit((HTTPConduit) config.getConduit(), connectionTimeoutMs);
    }

    public void initConduit(HTTPConduit conduit, int connectionTimeoutMs) {
        HTTPClientPolicy clientPolicy = conduit.getClient();
        clientPolicy.setVersion("1.1");
        clientPolicy.setAutoRedirect(false);
        clientPolicy.setAllowChunking(false);
        /*clientPolicy.setConnection(KEEP_ALIVE);*/
        clientPolicy.setConnectionTimeout(connectionTimeoutMs);

        TLSClientParameters tlsParams = new TLSClientParameters();
        // setDisableCNCheck and setHostnameVerifier should not be set
        // to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        tlsParams.setSslContext(gematikSslContext);
        // The JDK-HttpClient conduit builds its SSLContext from the trust managers and does not
        // honour a bare setSslContext(...), so without this the VAU/ePA handshake fell back to the
        // JDK default trust store (no TI CAs) and failed PKIX. Set our Gematik trust manager
        // (all TSL CAs + leaf-only chain completion) explicitly.
        tlsParams.setTrustManagers(new TrustManager[]{ gematikTrustManager });
        conduit.setTlsClientParameters(tlsParams);
    }
}
