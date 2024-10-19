package de.servicehealth.epa4all.serviceport;

import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureService;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateService;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.UserRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.xml.ws.BindingProvider;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

import static de.servicehealth.utils.TransportUtils.getFakeTrustManagers;

@ApplicationScoped
public class ServicePortProvider {

    private final KonnektorDefaultConfig konnektorDefaultConfig;

    private final SSLContext sslContext;

    @Inject
    public ServicePortProvider(KonnektorDefaultConfig konnektorDefaultConfig) throws Exception {
        this.konnektorDefaultConfig = konnektorDefaultConfig;
        sslContext = prepareSslContext();
    }

    @Produces
    @Singleton
    AuthenticatorClient getAuthenticatorClient() {
        return new AuthenticatorClient();
    }

    private SSLContext prepareSslContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] passCharArray = konnektorDefaultConfig.getCertAuthStoreFilePassword().toCharArray();
        FileInputStream pemInputStream = new FileInputStream(konnektorDefaultConfig.getCertAuthStoreFile());
        keyStore.load(pemInputStream, passCharArray);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, passCharArray);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), getFakeTrustManagers(), null);

        System.setProperty("javax.xml.accessExternalDTD", "all" );
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

        return sslContext;
    }

    public CertificateServicePortType getCertificateServicePort(UserRuntimeConfig userRuntimeConfig) {
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        CertificateService certificateService = new CertificateService();
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();
        setEndpointAddress((BindingProvider) certificateServicePort, konnektorUrl + "/ws/CertificateService", sslContext);

        return certificateServicePort;
    }

    public CardServicePortType getCardServicePortType(UserRuntimeConfig userRuntimeConfig) {
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        CardService cardService = new CardService();
        CardServicePortType cardServicePort = cardService.getCardServicePort();

        setEndpointAddress((BindingProvider) cardServicePort, konnektorUrl + "/ws/CardService", sslContext);

        return cardServicePort;
    }

    public AuthSignatureServicePortType getAuthSignatureServicePortType(UserRuntimeConfig userRuntimeConfig) {
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        AuthSignatureService authSignatureService = new AuthSignatureService();
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();

        setEndpointAddress((BindingProvider) authSignatureServicePort, konnektorUrl + "/ws/AuthSignatureService", sslContext);

        return authSignatureServicePort;
    }

    public EventServicePortType getEventServicePort(UserRuntimeConfig userRuntimeConfig) {
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        EventService eventService = new EventService();
        EventServicePortType eventServicePort = eventService.getEventServicePort();
        setEndpointAddress((BindingProvider) eventServicePort, konnektorUrl + "/ws/EventService", sslContext);

        return eventServicePort;
    }

    private void setEndpointAddress(BindingProvider bp, String url, SSLContext sslContext) {
        bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);

        // Get the CXF client proxy
        Client client = ClientProxy.getClient(bp);

        // Get the HTTPConduit
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();

        // Set TLS parameters
        TLSClientParameters tlsParams = new TLSClientParameters();
        tlsParams.setSSLSocketFactory(sslContext.getSocketFactory());
        tlsParams.setDisableCNCheck(true);  // Disable hostname verification (optional)

        // Set the TLS parameters on the HTTPConduit
        httpConduit.setTlsClientParameters(tlsParams);
    }
}
