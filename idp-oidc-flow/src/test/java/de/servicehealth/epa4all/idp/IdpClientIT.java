package de.servicehealth.epa4all.idp;

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
import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.common.PlainTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.transport.http.HTTPConduit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.yasson.JsonBindingProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initVauTransport;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class IdpClientIT {

    @Inject
    @ConfigProperty(name = "authorization-service.url")
    String authorizationServiceUrl;

    @Inject
    @ConfigProperty(name = "idp-service.url")
    String idpServiceUrl;

    @Inject
    @ConfigProperty(name = "konnektor.url")
    String konnektorUrl;

    @Inject
    @ConfigProperty(name = "incentergy.pem.path")
    String incentergyPemPath;

    @Inject
    @ConfigProperty(name = "incentergy.pem.pass")
    String incentergyPemPass;

    protected abstract <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception;

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

        // Load client keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(incentergyPemPath), incentergyPemPass.toCharArray());

        // Set KeyManagers and TrustManagers
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "N4rouwibGRhne2Fa".toCharArray());
        // Initialize SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), FakeTrustManager.getTrustManagers(), null);

        System.setProperty("javax.xml.accessExternalDTD", "all" );
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

        CertificateService certificateService = new CertificateService();
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();

        setEndpointAddress((BindingProvider) certificateServicePort, konnektorUrl + "/ws/CertificateService", sslContext);

        CardService cardService = new CardService();
        CardServicePortType cardServicePort = cardService.getCardServicePort();

        setEndpointAddress((BindingProvider) cardServicePort, konnektorUrl + "/ws/CardService", sslContext);

        AuthSignatureService authSignatureService = new AuthSignatureService();
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();

        setEndpointAddress((BindingProvider) authSignatureServicePort, konnektorUrl + "/ws/AuthSignatureService", sslContext);

        ContextType contextType = new ContextType();
        contextType.setMandantId("Incentergy");
        contextType.setClientSystemId("Incentergy");
        contextType.setWorkplaceId("1786_A1");

        EventService eventService = new EventService();
        EventServicePortType eventServicePort = eventService.getEventServicePort();

        setEndpointAddress((BindingProvider) eventServicePort, konnektorUrl + "/ws/EventService", sslContext);

        GetCards getCards = new GetCards();
        getCards.setContext(contextType);
        getCards.setCardType(CardTypeType.SMC_B);
        String smcbHandle = eventServicePort.getCards(getCards).getCards().getCard().get(0).getCardHandle();

        IdpClient idpClient = new IdpClient();
        idpClient.authenticatorClient = new AuthenticatorClient();

        String discoveryDocumentUrl = idpServiceUrl + "/.well-known/openid-configuration";
        idpClient.discoveryDocumentResponse = idpClient.authenticatorClient.retrieveDiscoveryDocument(
            discoveryDocumentUrl, Optional.empty()
        );

        VauClient vauClient = new VauClient(initVauTransport());

        idpClient.certificateServicePortType = certificateServicePort;
        idpClient.cardServicePortType = cardServicePort;
        idpClient.authSignatureServicePortType = authSignatureServicePort;
        idpClient.authorizationService1 = buildApi(vauClient, AuthorizationSmcBApi.class, authorizationServiceUrl);
        idpClient.authorizationService2 = buildApi(vauClient, AuthorizationSmcBApi.class, authorizationServiceUrl);
        idpClient.authorizationService3 = buildApi(vauClient, AuthorizationSmcBApi.class, authorizationServiceUrl);
        idpClient.contextType = contextType;
        idpClient.smcbHandle = smcbHandle;
        idpClient.getVauNp((String np) -> {
            System.out.println("NP: " + np);
            assertNotNull(np);
        });
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