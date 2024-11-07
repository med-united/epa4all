package de.servicehealth.epa4all.server.serviceport;

import static de.servicehealth.utils.SSLUtils.createSSLContext;
import static de.servicehealth.utils.SSLUtils.initSSLContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.xml.ws.BindingProvider;

@ApplicationScoped
public class KServicePortProvider {
	
	private static Logger log = Logger.getLogger(KServicePortProvider.class.getName());

    private final SSLContext defaultSSLContext;
    
    Map<IUserConfigurations, Map<String,String>> userConfigurations2endpointMap = new HashMap<>();

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
        CertificateService certificateService = new CertificateService();
        CertificateServicePortType certificateServicePort = certificateService.getCertificateServicePort();
        setEndpointAddress((BindingProvider) certificateServicePort,  userConfigurations2endpointMap.get(userRuntimeConfig.getUserConfigurations()).get("certificateServiceEndpointAddress"), sslContext);

        return certificateServicePort;
    }

    public CardServicePortType getCardServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        CardService cardService = new CardService();
        CardServicePortType cardServicePort = cardService.getCardServicePort();

        setEndpointAddress((BindingProvider) cardServicePort,  userConfigurations2endpointMap.get(userRuntimeConfig.getUserConfigurations()).get("cardServiceEndpointAddress"), sslContext);

        return cardServicePort;
    }

    public VSDServicePortType getVSDServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        VSDService vsdService = new VSDService();
        VSDServicePortType cardServicePort = vsdService.getVSDServicePort();

        setEndpointAddress((BindingProvider) cardServicePort,  userConfigurations2endpointMap.get(userRuntimeConfig.getUserConfigurations()).get("vsdServiceEndpointAddress"), sslContext);

        return cardServicePort;
    }

    public AuthSignatureServicePortType getAuthSignatureServicePortType(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        AuthSignatureService authSignatureService = new AuthSignatureService();
        AuthSignatureServicePortType authSignatureServicePort = authSignatureService.getAuthSignatureServicePort();

        setEndpointAddress((BindingProvider) authSignatureServicePort,  userConfigurations2endpointMap.get(userRuntimeConfig.getUserConfigurations()).get("authSignatureServiceEndpointAddress"), sslContext);

        return authSignatureServicePort;
    }

    public EventServicePortType getEventServicePort(UserRuntimeConfig userRuntimeConfig) {
        SSLContext sslContext = getSSLContext(userRuntimeConfig.getUserConfigurations());
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        EventService eventService = new EventService();
        EventServicePortType eventServicePort = eventService.getEventServicePort();
        setEndpointAddress((BindingProvider) eventServicePort, userConfigurations2endpointMap.get(userRuntimeConfig.getUserConfigurations()).get("eventServiceEndpointAddress"), sslContext);

        return eventServicePort;
    }

    private SSLContext getSSLContext(IUserConfigurations userConfigurations) {
        String certificate = userConfigurations.getClientCertificate();
        String certificatePassword = userConfigurations.getClientCertificatePassword();
        SSLContext sslContext = createSSLContext(certificate, certificatePassword, defaultSSLContext);
        lookupWebServiceURLsIfNecessary(sslContext, userConfigurations);
        return sslContext;
    }

    private void lookupWebServiceURLsIfNecessary(SSLContext sslContext, IUserConfigurations userConfigurations) {
    	if(userConfigurations2endpointMap.containsKey(userConfigurations)) {
    		return;
    	}
    	ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.sslContext(sslContext);

        // disable hostname verification
        clientBuilder = clientBuilder.hostnameVerifier(new HostnameVerifier() {

			@Override
			public boolean verify(String arg0, SSLSession arg1) {
				return true;
			}});
        if(userConfigurations.getConnectorBaseURL() == null) {
            log.warning("ConnectorBaseURL is null, won't read connector.sds");
            return;
        }

        Builder builder = clientBuilder.build()
                .target(userConfigurations.getConnectorBaseURL())
                .path("/connector.sds")
                .request();

        String basicAuthUsername = userConfigurations.getBasicAuthUsername();
        String basicAuthPassword = userConfigurations.getBasicAuthPassword();
        if(basicAuthUsername != null && !basicAuthUsername.equals("")) {
            builder.header("Authorization", "Basic "+Base64.getEncoder().encodeToString((basicAuthUsername+":"+basicAuthPassword).getBytes()));
        }
        Invocation invocation = builder
                .buildGet();

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
            userConfigurations2endpointMap.put(userConfigurations, endpointMap);

        } catch (ProcessingException | SAXException | IllegalArgumentException | IOException | ParserConfigurationException e) {
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
            if(version != null && versionNode.hasAttributes() && !versionNode.getAttributes().getNamedItem("Version").getTextContent().startsWith(version)) {
                continue;
            }

            Node endpointNode = getNodeWithTag(versionNode, "EndpointTLS");

            if (endpointNode == null || !endpointNode.hasAttributes()
                    || endpointNode.getAttributes().getNamedItem("Location") == null) {
                continue;
            }

            location = endpointNode.getAttributes().getNamedItem("Location").getTextContent();
            if (location.startsWith(userConfig.getConnectorBaseURL())) {
                return location;
            } else {
                log.warning("Invalid service node. Maybe location: "+location+" does not start with: "+userConfig.getConnectorBaseURL());
                return location;
            }
        }
        throw new IllegalArgumentException("Invalid service node. Maybe location: "+location+" does not start with: "+userConfig.getConnectorBaseURL());
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
