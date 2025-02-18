package de.servicehealth.epa4all.cxf.client;

import de.servicehealth.epa4all.cxf.interceptor.CxfHeadersInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbPlainReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauReaderProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbVauWriterProvider;
import de.servicehealth.epa4all.cxf.provider.JsonbWriterProvider;
import de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import java.util.List;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;

@ApplicationScoped
public class ClientFactory extends StartableService {

    @Inject
    VauConfig vauConfig;

    @Override
    public int getPriority() {
        return CxfClientFactoryPriority;
    }

    public void onStart() {
        Bus globalBus = BusFactory.getDefaultBus();
        globalBus.setProperty("force.urlconnection.http.conduit", false);
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
        JsonbVauReaderProvider jsonbVauReaderProvider = new JsonbVauReaderProvider();
        List<Object> providers = List.of(cborWriterProvider, jsonbVauWriterProvider, jsonbVauReaderProvider);
        T api = JAXRSClientFactory.create(url, clazz, providers, features, null);
        initClient(
            WebClient.getConfig(api),
            vauConfig.getConnectionTimeoutMs(),
            List.of(new LoggingOutInterceptor(), new CxfVauSetupInterceptor(vauFacade)),
            List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauFacade))
        );
        return api;
    }

    public static void initClient(
        ClientConfiguration config,
        int connectionTimeoutMs,
        List<Interceptor<? extends Message>> outInterceptors,
        List<Interceptor<? extends Message>> inInterceptors
    ) throws Exception {
        config.getOutInterceptors().addAll(outInterceptors);
        config.getInInterceptors().addAll(inInterceptors);

        initConduit((HTTPConduit) config.getConduit(), connectionTimeoutMs);
    }

    public static void initConduit(HTTPConduit conduit, int connectionTimeoutMs) throws Exception {
        HTTPClientPolicy clientPolicy = conduit.getClient();
        clientPolicy.setVersion("1.1");
        clientPolicy.setAutoRedirect(false);
        clientPolicy.setAllowChunking(false);
        /*clientPolicy.setConnection(KEEP_ALIVE);*/
        clientPolicy.setConnectionTimeout(connectionTimeoutMs);

        TLSClientParameters tlsParams = new TLSClientParameters();
        // setDisableCNCheck and setHostnameVerifier should not be set
        // to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        // tlsParams.setHostnameVerifier((hostname, session) -> true);
        tlsParams.setSslContext(createFakeSSLContext());
        conduit.setTlsClientParameters(tlsParams);
    }
}
