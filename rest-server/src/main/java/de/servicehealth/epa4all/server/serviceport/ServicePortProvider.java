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
import de.servicehealth.startup.StartableService;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.xml.ws.BindingProvider;
import lombok.Setter;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static de.servicehealth.utils.SSLUtils.createSSLContext;
import static de.servicehealth.utils.SSLUtils.initSSLContext;

@ApplicationScoped
public class ServicePortProvider extends StartableService {

    private static final Logger log = Logger.getLogger(ServicePortProvider.class.getName());

    private SSLContext defaultSSLContext;

    @Setter
    Map<String, Map<String, String>> konnektorsEndpoints = new HashMap<>();

    LoggingFeature loggingFeature = new LoggingFeature();

    // this must be started after MultiEpaService
    public void onStart() throws Exception {
        konnektorsEndpoints.putAll(new ServicePortFile(configDirectory).get());

        loggingFeature.setPrettyLogging(true);
        loggingFeature.setVerbose(true);
        loggingFeature.setLogMultipart(true);
        loggingFeature.setLogBinary(false);
    }

    void saveEndpointsConfiguration() {
        try {
            new ServicePortFile(configDirectory).update(konnektorsEndpoints);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while saving service-ports file", e);
        }
    }

    @Inject
    public ServicePortProvider(KonnektorDefaultConfig konnektorDefaultConfig) {
        Optional<String> certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        Optional<String> certAuthStoreFilePassword = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        if (certAuthStoreFile.isPresent() && certAuthStoreFilePassword.isPresent()) {
            String certPass = certAuthStoreFilePassword.get();
            try (FileInputStream certInputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLResult sslResult = initSSLContext(certInputStream, certPass);
                defaultSSLContext = sslResult.getSslContext();
            } catch (Exception e) {
                log.log(Level.SEVERE, "There was a problem when creating the SSLContext", e);
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

    public CertificateServicePortType getCertificateServicePort(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        CertificateService certificateService = new CertificateService(loggingFeature);
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();
        String connectorBaseURL = userRuntimeConfig.getUserConfigurations().getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get("certificateServiceEndpointAddress");
        setEndpointAddress((BindingProvider) certificateServicePort, url, sslContext);

        return certificateServicePort;
    }

    public CardServicePortType getCardServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        CardService cardService = new CardService(loggingFeature);
        CardServicePortType cardServicePort = cardService.getCardServicePort();
        String connectorBaseURL = userRuntimeConfig.getUserConfigurations().getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get("cardServiceEndpointAddress");
        setEndpointAddress((BindingProvider) cardServicePort, url, sslContext);

        return cardServicePort;
    }

    public VSDServicePortType getVSDServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        VSDService vsdService = new VSDService(loggingFeature);
        VSDServicePortType cardServicePort = vsdService.getVSDServicePort();
        String connectorBaseURL = userRuntimeConfig.getUserConfigurations().getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get("vsdServiceEndpointAddress");
        setEndpointAddress((BindingProvider) cardServicePort, url, sslContext);

        return cardServicePort;
    }

    public AuthSignatureServicePortType getAuthSignatureServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        AuthSignatureService authSignatureService = new AuthSignatureService(loggingFeature);
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();
        String connectorBaseURL = userRuntimeConfig.getUserConfigurations().getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get("authSignatureServiceEndpointAddress");
        setEndpointAddress((BindingProvider) authSignatureServicePort, url, sslContext);

        return authSignatureServicePort;
    }

    public EventServicePortType getEventServicePort(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        EventService eventService = new EventService(loggingFeature);
        EventServicePortType eventServicePort = eventService.getEventServicePort();
        String connectorBaseURL = userRuntimeConfig.getUserConfigurations().getConnectorBaseURL();
        String url = konnektorsEndpoints.get(connectorBaseURL).get("eventServiceEndpointAddress");
        setEndpointAddress((BindingProvider) eventServicePort, url, sslContext);

        return eventServicePort;
    }

    private SSLContext getSSLContext(IUserConfigurations userConfigurations) {
        String certificate = userConfigurations.getClientCertificate();
        String certificatePassword = userConfigurations.getClientCertificatePassword();
        SSLContext sslContext = createSSLContext(certificate, certificatePassword, defaultSSLContext);
        lookupWebServiceURLsIfNecessary(sslContext, userConfigurations);
        return sslContext;
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
            log.warning("ConnectorBaseURL is null, won't read connector.sds");
            return;
        }

        Builder builder = clientBuilder.build()
            .target(userConfigurations.getConnectorBaseURL())
            .path("/connector.sds")
            .request();

        String basicAuthUsername = userConfigurations.getBasicAuthUsername();
        String basicAuthPassword = userConfigurations.getBasicAuthPassword();
        if (basicAuthUsername != null && !basicAuthUsername.isEmpty()) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((basicAuthUsername + ":" + basicAuthPassword).getBytes()));
        }
        Invocation invocation = builder.buildGet();

        try (InputStream inputStream = invocation.invoke(InputStream.class)) {
            Document document = DocumentBuilderFactory.newDefaultInstance()
                .newDocumentBuilder()
                .parse(inputStream);

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
                        endpointMap.put("authSignatureServiceEndpointAddress", getEndpoint(node, null, userConfigurations));
                        break;
                    }
                    case "CardService": {
                        endpointMap.put("cardServiceEndpointAddress", getEndpoint(node, null, userConfigurations));
                        break;
                    }
                    case "EventService": {
                        endpointMap.put("eventServiceEndpointAddress", getEndpoint(node, null, userConfigurations));
                        break;
                    }
                    case "CertificateService": {
                        endpointMap.put("certificateServiceEndpointAddress", getEndpoint(node, null, userConfigurations));
                        break;
                    }
                    case "SignatureService": {
                        endpointMap.put("signatureServiceEndpointAddress", getEndpoint(node, "7.5", userConfigurations));
                    }
                    case "VSDService": {
                        endpointMap.put("vsdServiceEndpointAddress", getEndpoint(node, null, userConfigurations));
                    }
                }
            }
            konnektorsEndpoints.put(userConfigurations.getConnectorBaseURL(), endpointMap);

        } catch (ProcessingException | SAXException | IllegalArgumentException | IOException |
                 ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

    }

    private String getEndpoint(Node serviceNode, String version, IUserConfigurations userConfig) {
        Node versionsNode = getNodeWithTag(serviceNode, "Versions");

        if (versionsNode == null) {
            throw new IllegalArgumentException("No version tags found");
        }
        NodeList versionNodes = versionsNode.getChildNodes();
        String location = "";
        for (int i = 0, n = versionNodes.getLength(); i < n; ++i) {
            Node versionNode = versionNodes.item(i);

            // if we have a specified version search in the list until we find it
            if (version != null && versionNode.hasAttributes() && !versionNode.getAttributes().getNamedItem("Version").getTextContent().startsWith(version)) {
                continue;
            }

            Node endpointNode = getNodeWithTag(versionNode, "EndpointTLS");

            if (endpointNode == null || !endpointNode.hasAttributes()
                || endpointNode.getAttributes().getNamedItem("Location") == null) {
                continue;
            }

            location = endpointNode.getAttributes().getNamedItem("Location").getTextContent();
            if (!location.startsWith(userConfig.getConnectorBaseURL())) {
                log.warning("Invalid service node. Maybe location: " + location + " does not start with: " + userConfig.getConnectorBaseURL());
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
