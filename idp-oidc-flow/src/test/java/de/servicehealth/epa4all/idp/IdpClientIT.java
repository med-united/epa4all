package de.servicehealth.epa4all.idp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.transport.http.HTTPConduit;
import org.eclipse.yasson.JsonBindingProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.junit.jupiter.api.Test;

import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureService;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateService;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage;
import de.servicehealth.epa4all.authorization.AuthorizationSmcBApi;
import jakarta.xml.ws.BindingProvider;
import kong.unirest.core.Config;
import kong.unirest.core.HttpRequest;
import kong.unirest.core.HttpRequestSummary;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Interceptor;
import kong.unirest.core.Unirest;

public class IdpClientIT {

    @Test
    public void testGetVauNp() throws NoSuchAlgorithmException, CertificateException, FileNotFoundException, KeyStoreException, IOException, KeyManagementException, UnrecoverableKeyException, FaultMessage {


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

        // Load client keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("/home/manuel/Desktop/RU-Connector-Cert/incentergy.p12"), "N4rouwibGRhne2Fa".toCharArray());

        // Set KeyManagers and TrustManagers
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "N4rouwibGRhne2Fa".toCharArray());
        // Initialize SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), FakeTrustManager.getTrustManagers(), null);
        
        System.setProperty("javax.xml.accessExternalDTD", "all" );
        
        JsonBindingProvider provider = new JsonBindingProvider();
        List<Object> providers = new ArrayList<>();
        providers.add(provider);
        providers.add(new JacksonJsonProvider());
        String authorizationServiceUrl = "http://localhost:8083/";
        // String authorizationServiceUrl = "http://epa-as-1.dev.epa4all.de";
        AuthorizationSmcBApi api = JAXRSClientFactory.create(
            authorizationServiceUrl, AuthorizationSmcBApi.class, providers
        );

        CertificateService certificateService = new CertificateService();
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();

        setEndpointAddress((BindingProvider) certificateServicePort, "https://192.168.178.42:443/ws/CertificateService", sslContext);

        CardService cardService = new CardService();
        CardServicePortType cardServicePort = cardService.getCardServicePort();

        setEndpointAddress((BindingProvider) cardServicePort, "https://192.168.178.42:443/ws/CardService", sslContext);

        AuthSignatureService authSignatureService = new AuthSignatureService();
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();

        setEndpointAddress((BindingProvider) authSignatureServicePort, "https://192.168.178.42:443/ws/AuthSignatureService", sslContext);

        ContextType contextType = new ContextType();
        contextType.setMandantId("Incentergy");
        contextType.setClientSystemId("Incentergy");
        contextType.setWorkplaceId("1786_A1");

        EventService eventService = new EventService();
        EventServicePortType eventServicePort = eventService.getEventServicePort();

        setEndpointAddress((BindingProvider) eventServicePort, "https://192.168.178.42:443/ws/EventService", sslContext);

        GetCards getCards = new GetCards();
        getCards.setContext(contextType);
        getCards.setCardType(CardTypeType.SMC_B);
        String smcbHandle = eventServicePort.getCards(getCards).getCards().getCard().get(0).getCardHandle();

        IdpClient idpClient = new IdpClient();
        idpClient.authenticatorClient = new AuthenticatorClient();
        idpClient.discoveryDocumentResponse = idpClient.authenticatorClient.retrieveDiscoveryDocument("https://idp-ref.zentral.idp.splitdns.ti-dienste.de/.well-known/openid-configuration", Optional.empty());
        idpClient.certificateServicePortType = certificateServicePort;
        idpClient.cardServicePortType = cardServicePort;
        idpClient.authSignatureServicePortType = authSignatureServicePort;
        idpClient.authorizationService = api;
        idpClient.contextType = contextType;
        idpClient.smcbHandle = smcbHandle;
        idpClient.getVauNp((String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
    }

    private void setEndpointAddress(BindingProvider bp, String url, SSLContext sslContext) throws NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyStoreException, KeyManagementException, UnrecoverableKeyException {
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