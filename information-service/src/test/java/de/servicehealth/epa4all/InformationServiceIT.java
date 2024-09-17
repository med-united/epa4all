package de.servicehealth.epa4all;

import static de.servicehealth.epa4all.common.Utils.createFakeSSLContext;
import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.yasson.JsonBindingProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.common.DevTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class InformationServiceIT {

    private final static Logger log = LoggerFactory.getLogger(InformationServiceIT.class);

    @Inject
    @ConfigProperty(name = "information-service.url")
    String informationServiceUrl;

    @Test
    public void callInformationServiceWorks() throws Exception {
        if (isDockerServiceRunning("information-service")) {
            JsonBindingProvider provider = new JsonBindingProvider();
            List<JsonBindingProvider> providers = new ArrayList<>();
            providers.add(provider);

            // Bus bus = BusFactory.getThreadDefaultBus();
            // DestinationFactoryManagerImpl dfm = bus.getExtension(DestinationFactoryManagerImpl.class);
            // HTTPVAUTransportFactory customTransport = new HTTPVAUTransportFactory();
            // dfm.registerDestinationFactory(HTTPVAUTransportFactory.TRANSPORT_IDENTIFIER, customTransport);
            //
            // ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
            // extension.registerConduitInitiator(HTTPVAUTransportFactory.TRANSPORT_IDENTIFIER, customTransport);


            AccountInformationApi api = JAXRSClientFactory.create(
                informationServiceUrl, AccountInformationApi.class, providers
            );
            Client client = WebClient.client(api);
            ClientConfiguration config = WebClient.getConfig(client);
            HTTPConduit conduit = (HTTPConduit) config.getConduit();
            conduit.getClient().setVersion("1.1");

            TLSClientParameters tlsParams = conduit.getTlsClientParameters();
            if (tlsParams == null) {
                tlsParams = new TLSClientParameters();
                conduit.setTlsClientParameters(tlsParams);
            }

            tlsParams.setSslContext(createFakeSSLContext());
            tlsParams.setDisableCNCheck(true);
            tlsParams.setHostnameVerifier((hostname, session) -> true);

            config.getOutInterceptors().add(new LoggingOutInterceptor());
            config.getInInterceptors().add(new LoggingInInterceptor());

            assertDoesNotThrow(() -> api.getRecordStatus("Z1234567890", "PSSIM123456789012345/1.2.4"));
        } else {
            log.warn("Docker container for information-service is not running, skipping a test");
        }
    }
}
