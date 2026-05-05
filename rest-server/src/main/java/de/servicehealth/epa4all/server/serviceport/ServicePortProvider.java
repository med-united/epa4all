package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureService;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.cardterminalservice.wsdl.v1_1.CardTerminalService;
import de.gematik.ws.conn.cardterminalservice.wsdl.v1_1.CardTerminalServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateService;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDService;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.health.service.cetp.config.KonnektorAuth;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.api.epa4all.annotation.KonnektorSoapFeatures;
import de.servicehealth.startup.StartableService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static de.servicehealth.utils.SSLUtils.createSSLContext;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY;
import static jakarta.xml.ws.BindingProvider.PASSWORD_PROPERTY;
import static jakarta.xml.ws.BindingProvider.USERNAME_PROPERTY;

@ApplicationScoped
public class ServicePortProvider extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(ServicePortProvider.class.getName());

    private static final Pattern IP_V4_REGEXP = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );

    @Setter
    Map<String, Map<String, String>> konnektorsEndpoints = new HashMap<>();

    @Inject
    @KonnektorSoapFeatures
    List<WebServiceFeature> konnektorSoapFeatures;

    @Inject
    @Named("konnektorDefaultSSLContext")
    SSLContext konnektorDefaultSSLContext;

    @Inject
    X509TrustManager trustManager;

    /**
     * Optional dedicated trust store for Konnektoren that present a self-signed (or
     * private-CA-signed) TLS certificate not anchored in the Gematik TSL. When present,
     * this trust manager is used in place of {@link #trustManager} for Konnektor
     * connections only — narrowing the trust set rather than disabling verification.
     * See {@link de.servicehealth.epa4all.server.cdi.KonnektorSelfSignedTrustManagerProvider}.
     */
    @Inject
    @Named("konnektorSelfSignedTrustManager")
    Optional<X509TrustManager> konnektorSelfSignedTrustManager;

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

    public CardTerminalServicePortType getCardTerminalServicePortType(IUserConfigurations userConfigurations) {
        CardTerminalServicePortType cardTerminalServicePort = new CardTerminalService(getFeatures()).getCardTerminalServicePort();
        initServicePort(userConfigurations, (BindingProvider) cardTerminalServicePort, "cardTerminalServiceEndpointAddress");
        return cardTerminalServicePort;
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
        X509TrustManager effectiveTm = konnektorSelfSignedTrustManager.orElse(trustManager);
        SSLContext sslContext = createSSLContext(certificate, password, effectiveTm, konnektorDefaultSSLContext);

        lookupWebServiceURLsIfNecessary(sslContext, userConfigurations, endpointKey);

        String connectorBaseURL = userConfigurations.getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get(endpointKey);
        setEndpointAddress(servicePort, url, sslContext, userConfigurations);
    }

    private boolean isLocalhostOrIPv4(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || IP_V4_REGEXP.matcher(host).matches();
    }

    private void setEndpointAddress(
        BindingProvider bindingProvider,
        String url,
        SSLContext sslContext,
        IUserConfigurations userConfigurations
    ) {
        bindingProvider.getRequestContext().put(ENDPOINT_ADDRESS_PROPERTY, url);

        TLSClientParameters tlsParams = new TLSClientParameters();
        if (isLocalhostOrIPv4(url)) {
            // The Konnektor is addressed by IP on the praxis LAN, while its certificate usually
            // contains a DNS SAN such as "konnektor.konlan" rather than the configured IP address.
            // Therefore, standard hostname verification would fail.
            //
            // We still keep full certificate path validation enabled via the TrustManager built
            // from the Gematik TSL / TI trust anchors. Only the endpoint name check is disabled.
            //
            // Security assumption: the praxis LAN and Konnektor IP configuration are trusted and
            // protected. Do not use this as a general TLS policy for arbitrary remote endpoints.
            tlsParams.setDisableCNCheck(true);
        }

        switch (KonnektorAuth.from(userConfigurations.getAuth())) {
            case BASIC -> {
                bindingProvider.getRequestContext().put(USERNAME_PROPERTY, userConfigurations.getBasicAuthUsername());
                bindingProvider.getRequestContext().put(PASSWORD_PROPERTY, userConfigurations.getBasicAuthPassword());
            }
            case CERTIFICATE -> tlsParams.setSSLSocketFactory(sslContext.getSocketFactory());
        }

        Client client = ClientProxy.getClient(bindingProvider);
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        httpConduit.setTlsClientParameters(tlsParams);
    }

    @SuppressWarnings("resource")
    private void lookupWebServiceURLsIfNecessary(
        SSLContext sslContext,
        IUserConfigurations userConfigurations,
        String endpointKey
    ) {
        String connectorBaseURL = userConfigurations.getConnectorBaseURL();
        Map<String, String> konnektorEndpoints = konnektorsEndpoints.get(connectorBaseURL);
        if (konnektorEndpoints != null && konnektorEndpoints.containsKey(endpointKey)) {
            return;
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.sslContext(sslContext);

        if (connectorBaseURL == null) {
            log.warn("ConnectorBaseURL is null, won't read connector.sds");
            return;
        }
        if (isLocalhostOrIPv4(connectorBaseURL)) {
            // The Konnektor is addressed by IP on the praxis LAN, while its certificate usually
            // contains a DNS SAN such as "konnektor.konlan" rather than the configured IP address.
            // Therefore, standard hostname verification would fail.
            //
            // We still keep full certificate path validation enabled via the TrustManager built
            // from the Gematik TSL / TI trust anchors. Only the endpoint name check is disabled.
            //
            // Security assumption: the praxis LAN and Konnektor IP configuration are trusted and
            // protected. Do not use this as a general TLS policy for arbitrary remote endpoints.
            clientBuilder = clientBuilder.hostnameVerifier((h, s) -> true);
        }
        Builder builder = clientBuilder.build()
            .target(connectorBaseURL)
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

                // TODO move needed servicePorts to config
                switch (node.getAttributes().getNamedItem("Name").getTextContent()) {
                    case "AuthSignatureService": {
                        endpointMap.put("authSignatureServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "CardService": {
                        endpointMap.put("cardServiceEndpointAddress", getEndpoint(node, userConfigurations));
                        break;
                    }
                    case "CardTerminalService": {
                        endpointMap.put("cardTerminalServiceEndpointAddress", getEndpoint(node, userConfigurations));
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
            konnektorsEndpoints.put(connectorBaseURL, endpointMap);

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
