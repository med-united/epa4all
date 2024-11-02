package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureService;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateService;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDService;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;

import static de.servicehealth.utils.SSLUtils.createSSLContext;
import static de.servicehealth.utils.SSLUtils.initSSLContext;

@ApplicationScoped
public class KServicePortProvider {

    private final SSLContext defaultSSLContext;

    @Inject
    public KServicePortProvider(KonnektorDefaultConfig konnektorDefaultConfig) throws Exception {
        String certPass = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        try (FileInputStream certInputStream = new FileInputStream(konnektorDefaultConfig.getCertAuthStoreFile())) {
            SSLResult sslResult = initSSLContext(certInputStream, certPass);
            defaultSSLContext = sslResult.getSslContext();
        }
    }

    public CertificateServicePortType getCertificateServicePort(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        CertificateService certificateService = new CertificateService();
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();
        setEndpointAddress((BindingProvider) certificateServicePort, konnektorUrl + "/ws/CertificateService", sslContext);

        return certificateServicePort;
    }

    public CardServicePortType getCardServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        CardService cardService = new CardService();
        CardServicePortType cardServicePort = cardService.getCardServicePort();

        setEndpointAddress((BindingProvider) cardServicePort, konnektorUrl + "/ws/CardService", sslContext);

        return cardServicePort;
    }

    public VSDServicePortType getVSDServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        VSDService vsdService = new VSDService();
        VSDServicePortType cardServicePort = vsdService.getVSDServicePort();

        setEndpointAddress((BindingProvider) cardServicePort, konnektorUrl + "/ws/VSDService", sslContext);

        return cardServicePort;
    }

    public AuthSignatureServicePortType getAuthSignatureServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        AuthSignatureService authSignatureService = new AuthSignatureService();
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();

        setEndpointAddress((BindingProvider) authSignatureServicePort, konnektorUrl + "/ws/AuthSignatureService", sslContext);

        return authSignatureServicePort;
    }

    public EventServicePortType getEventServicePort(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        EventService eventService = new EventService();
        EventServicePortType eventServicePort = eventService.getEventServicePort();
        setEndpointAddress((BindingProvider) eventServicePort, konnektorUrl + "/ws/EventService", sslContext);

        return eventServicePort;
    }

    private SSLContext getSSLContext(IUserConfigurations userConfigurations) {
        String certificate = userConfigurations.getClientCertificate();
        String certificatePassword = userConfigurations.getClientCertificatePassword();
        return createSSLContext(certificate, certificatePassword, defaultSSLContext);
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
