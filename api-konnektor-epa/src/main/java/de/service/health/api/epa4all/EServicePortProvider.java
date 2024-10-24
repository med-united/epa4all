package de.service.health.api.epa4all;

import de.servicehealth.epa4all.cxf.VauClientFactory;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteInterceptor;
import de.servicehealth.vau.VauClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import javax.xml.namespace.QName;
import java.util.List;

import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static org.apache.cxf.transports.http.configuration.ConnectionType.KEEP_ALIVE;

@ApplicationScoped
public class EServicePortProvider {

    private final VauClient vauClient;

    @Inject
    public EServicePortProvider(VauClientFactory vauClientFactory) {
        this.vauClient = vauClientFactory.getVauClient();
    }

    // TODO Feature

    public IDocumentManagementPortType getDocumentManagementPortType(String documentManagementUrl) throws Exception {
        IDocumentManagementPortType documentManagement = createXDSDocumentPortType(
            documentManagementUrl, IDocumentManagementPortType.class
        );
        initPortType(documentManagement);
        return documentManagement;
    }

    public IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType(String documentManagementUrl) throws Exception {
        IDocumentManagementInsurantPortType documentManagementInsurant = createXDSDocumentPortType(
            documentManagementUrl, IDocumentManagementInsurantPortType.class
        );
        initPortType(documentManagementInsurant);
        return documentManagementInsurant;
    }

    private <T> T createXDSDocumentPortType(String address, Class<T> clazz) {
        JaxWsProxyFactoryBean jaxWsProxyFactory = new JaxWsProxyFactoryBean();
        jaxWsProxyFactory.setServiceClass(XDSDocumentService.class);
        jaxWsProxyFactory.setServiceName(new QName("urn:ihe:iti:xds-b:2007", "XDSDocumentService"));
        jaxWsProxyFactory.setAddress(address);
        jaxWsProxyFactory.getOutInterceptors().addAll(List.of(new LoggingOutInterceptor(), new CxfVauWriteInterceptor(vauClient)));
        jaxWsProxyFactory.getInInterceptors().addAll(List.of(new LoggingInInterceptor(), new CxfVauReadInterceptor(vauClient)));
        return jaxWsProxyFactory.create(clazz);
    }

    private void initPortType(Object portType) throws Exception {
        Client client = ClientProxy.getClient(portType);

        // TODO ClientFactory.initClient()

        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy clientPolicy = conduit.getClient();
        clientPolicy.setVersion("1.1");
        clientPolicy.setAutoRedirect(false);
        clientPolicy.setAllowChunking(false);
        clientPolicy.setConnection(KEEP_ALIVE);

        TLSClientParameters tlsParams = new TLSClientParameters();
        // setDisableCNCheck and setHostnameVerifier should not be set
        // to stick to HttpClientHTTPConduit (see HttpClientHTTPConduit.setupConnection)
        tlsParams.setSslContext(createFakeSSLContext());
        conduit.setTlsClientParameters(tlsParams);
    }
}
