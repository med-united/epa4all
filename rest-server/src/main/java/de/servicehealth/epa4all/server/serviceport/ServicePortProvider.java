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
import de.health.service.cetp.config.KonnektorAuth;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.api.epa4all.annotation.KonnektorSoapFeatures;
import de.servicehealth.startup.StartableService;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceFeature;
import lombok.Setter;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.health.service.cetp.config.KonnektorAuth.CERTIFICATE;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static de.servicehealth.utils.SSLUtils.createSSLContext;
import static de.servicehealth.utils.SSLUtils.initSSLContext;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY;
import static jakarta.xml.ws.BindingProvider.PASSWORD_PROPERTY;
import static jakarta.xml.ws.BindingProvider.USERNAME_PROPERTY;

@ApplicationScoped
public class ServicePortProvider extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(ServicePortProvider.class.getName());

    private SSLContext defaultSSLContext;

    @Setter
    Map<String, Map<String, String>> konnektorsEndpoints = new HashMap<>();

    @Inject
    @KonnektorSoapFeatures
    List<WebServiceFeature> konnektorSoapFeatures;

    // this must be started after EpaMultiService
    public void doStart() throws Exception {
        konnektorsEndpoints.putAll(new ServicePortFile(configDirectory).get());
    }

    void saveEndpointsConfiguration() {
        try {
            new ServicePortFile(configDirectory).overwrite(konnektorsEndpoints);
        } catch (Exception e) {
            log.error("Error while saving service-ports file", e);
        }
    }

    @Inject
    public ServicePortProvider(KonnektorDefaultConfig konnektorDefaultConfig) {
        Optional<KonnektorAuth> auth = konnektorDefaultConfig.getAuth();
        Optional<String> certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        Optional<String> certAuthStoreFilePassword = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        if (auth.isPresent()
            && auth.get() == CERTIFICATE
            && certAuthStoreFile.isPresent()
            && certAuthStoreFilePassword.isPresent()
        ) {
            String certPass = certAuthStoreFilePassword.get();
            try (FileInputStream certInputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLResult sslResult = initSSLContext(certInputStream, certPass);
                defaultSSLContext = sslResult.getSslContext();
            } catch (Exception e) {
                log.warn("There was a problem when creating the SSLContext: " + e.getMessage());
                defaultSSLContext = createFakeDefaultSSLContext();
            }
        } else {
            defaultSSLContext = createFakeDefaultSSLContext();
        }
    }

    private SSLContext createFakeDefaultSSLContext() {
        try {
            return createFakeSSLContext();
        } catch (Exception e) {
            return null;
        }
    }

    private WebServiceFeature[] getFeatures() {
        return konnektorSoapFeatures.toArray(WebServiceFeature[]::new);
    }

    public CertificateServicePortType getCertificateServicePort(IUserConfigurations userConfigurations) {
        CertificateServicePortType certificateServicePort = new CertificateService(getFeatures()).getCertificateServicePort();
        initServicePort(userConfigurations, (BindingProvider) certificateServicePort, "certificateServiceEndpointAddress");
        return certificateServicePort;
    }

    public CardServicePortType getCardServicePortType(IUserConfigurations userConfigurations) {
        CardServicePortType cardServicePort = new CardService(getFeatures()).getCardServicePort();
        initServicePort(userConfigurations, (BindingProvider) cardServicePort, "cardServiceEndpointAddress");
        return cardServicePort;
    }

    public VSDServicePortType getVSDServicePortType(IUserConfigurations userConfigurations) {
        VSDServicePortType vsdServicePort = new VSDService(getFeatures()).getVSDServicePort();
        initServicePort(userConfigurations, (BindingProvider) vsdServicePort, "vsdServiceEndpointAddress");
        return vsdServicePort;
    }

    public AuthSignatureServicePortType getAuthSignatureServicePortType(IUserConfigurations userConfigurations) {
        AuthSignatureServicePortType authSignatureServicePort = new AuthSignatureService(getFeatures()).getAuthSignatureServicePort();
        initServicePort(userConfigurations, (BindingProvider) authSignatureServicePort, "authSignatureServiceEndpointAddress");
        return authSignatureServicePort;
    }

    public EventServicePortType getEventServicePortSilent(IUserConfigurations userConfigurations) {
        EventServicePortType eventServicePort = new EventService().getEventServicePort();
        initServicePort(userConfigurations, (BindingProvider) eventServicePort, "eventServiceEndpointAddress");
        return eventServicePort;
    }

    public EventServicePortType getEventServicePort(IUserConfigurations userConfigurations) {
        EventServicePortType eventServicePort = new EventService(getFeatures()).getEventServicePort();
        initServicePort(userConfigurations, (BindingProvider) eventServicePort, "eventServiceEndpointAddress");
        return eventServicePort;
    }

    private void initServicePort(IUserConfigurations userConfigurations, BindingProvider servicePort, String endpointKey) {
        String certificate = userConfigurations.getClientCertificate();
        String password = userConfigurations.getClientCertificatePassword();
        SSLContext sslContext = certificate == null
            ? defaultSSLContext
            : createSSLContext(certificate, password, defaultSSLContext);

        lookupWebServiceURLsIfNecessary(sslContext, userConfigurations);

        String connectorBaseURL = userConfigurations.getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get(endpointKey);
        setEndpointAddress(servicePort, url, sslContext, userConfigurations);
    }

    private void setEndpointAddress(
        BindingProvider bindingProvider,
        String url,
        SSLContext sslContext,
        IUserConfigurations userConfigurations
    ) {
        bindingProvider.getRequestContext().put(ENDPOINT_ADDRESS_PROPERTY, url);

        TLSClientParameters tlsParams = new TLSClientParameters();
        tlsParams.setDisableCNCheck(true);  // Disable hostname verification

        switch (userConfigurations.getKonnektorAuth()) {
            case BASIC -> {
                bindingProvider.getRequestContext().put(USERNAME_PROPERTY, userConfigurations.getBasicAuthUsername());
                bindingProvider.getRequestContext().put(PASSWORD_PROPERTY, userConfigurations.getBasicAuthPassword());
            }
            case CERTIFICATE ->
                tlsParams.setSSLSocketFactory(sslContext.getSocketFactory());
        }

        Client client = ClientProxy.getClient(bindingProvider);
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        httpConduit.setTlsClientParameters(tlsParams);
    }

    @SuppressWarnings("resource")
    private void lookupWebServiceURLsIfNecessary(SSLContext sslContext, IUserConfigurations userConfigurations) {
        if (konnektorsEndpoints.containsKey(userConfigurations.getConnectorBaseURL())) {
            return;
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.sslContext(sslContext);

        // disable hostname verification
        clientBuilder = clientBuilder.hostnameVerifier((h, s) -> true);
        if (userConfigurations.getConnectorBaseURL() == null) {
            log.warn("ConnectorBaseURL is null, won't read connector.sds");
            return;
        }

        Builder builder = clientBuilder.build()
            .target(userConfigurations.getConnectorBaseURL())
            .path("/connector.sds")
            .request();

        String username = userConfigurations.getBasicAuthUsername();
        String password = userConfigurations.getBasicAuthPassword();
        if (username != null && !username.isEmpty()) {
            String basicCreds = username + ":" + password;
            builder.header(AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(basicCreds.getBytes()));
        }
        Invocation invocation = builder.buildGet();

        try (InputStream inputStream = invocation.invoke(InputStream.class)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            Node serviceInformationNode = getNodeWithTag(document.getDocumentElement(), "ServiceInformation");

            if (serviceInformationNode == null) {
                throw new IllegalArgumentException("Could not find single 'ServiceInformation'-tag");
            }

            NodeList serviceNodeList = serviceInformationNode.getChildNodes();

            Map<String, String> endpointMap = new HashMap<>();
            for (int i = 0, n = serviceNodeList.getLength(); i < n; ++i) {
                Node node = serviceNodeList.item(i);

                if (node.getNodeType() != 1) {
                    // ignore formatting related text nodes
                    continue;
                }

                if (!node.hasAttributes() || node.getAttributes().getNamedItem("Name") == null) {
                    break;
                }

                switch (node.getAttributes().getNamedItem("Name").getTextContent()) {
                    case "AuthSignatureService": {
                        endpointMap.put("authSignatureServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "CardService": {
                        endpointMap.put("cardServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "EventService": {
                        endpointMap.put("eventServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "CertificateService": {
                        endpointMap.put("certificateServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "VSDService": {
                        endpointMap.put("vsdServiceEndpointAddress", getEndpoint(node, userConfigurations));
                    }
                }
            }
            konnektorsEndpoints.put(userConfigurations.getConnectorBaseURL(), endpointMap);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getEndpoint(Node serviceNode, IUserConfigurations userConfig) {
        Node versionsNode = getNodeWithTag(serviceNode, "Versions");

        if (versionsNode == null) {
            throw new IllegalArgumentException("No version tags found");
        }
        NodeList versionNodes = versionsNode.getChildNodes();
        String location = "";
        for (int i = 0, n = versionNodes.getLength(); i < n; ++i) {
            Node versionNode = versionNodes.item(i);
            Node endpointNode = getNodeWithTag(versionNode, "EndpointTLS");

            if (endpointNode == null || !endpointNode.hasAttributes()
                || endpointNode.getAttributes().getNamedItem("Location") == null) {
                continue;
            }

            location = endpointNode.getAttributes().getNamedItem("Location").getTextContent();
            if (!location.startsWith(userConfig.getConnectorBaseURL())) {
                log.warn("Invalid service node. Maybe location: " + location + " does not start with: " + userConfig.getConnectorBaseURL());
            }
            return location;
        }
        throw new IllegalArgumentException("Invalid service node. Maybe location: " + location + " does not start with: " + userConfig.getConnectorBaseURL());
    }

    private Node getNodeWithTag(Node node, String tagName) {
        NodeList nodeList = node.getChildNodes();

        for (int i = 0, n = nodeList.getLength(); i < n; ++i) {
            Node childNode = nodeList.item(i);

            // ignore namespace entirely
            if (tagName.equals(childNode.getNodeName()) || childNode.getNodeName().endsWith(":" + tagName)) {
                return childNode;
            }
        }
        return null;
    }
}
