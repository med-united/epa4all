package de.service.health.api.epa4all;

import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadSoapInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteSoapInterceptor;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.soap.SOAPBinding;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.ws.addressing.WSAddressingFeature;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initConduit;
import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;

@ApplicationScoped
public class EpaServicePortProvider {

    public IDocumentManagementPortType getDocumentManagementPortType(
        String url,
        String epaUserAgent,
        VauFacade vauFacade,
        VauConfig vauConfig
    ) throws Exception {
        return createXDSDocumentPortType(url, epaUserAgent, IDocumentManagementPortType.class, vauFacade, vauConfig);
    }

    public IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType(
        String url,
        String epaUserAgent,
        VauFacade vauFacade,
        VauConfig vauConfig
    ) throws Exception {
        return createXDSDocumentPortType(url, epaUserAgent, IDocumentManagementInsurantPortType.class, vauFacade, vauConfig);
    }

    private <T> T createXDSDocumentPortType(
        String address,
        String epaUserAgent,
        Class<T> clazz,
        VauFacade vauFacade,
        VauConfig vauConfig
    ) throws Exception {
        JaxWsProxyFactoryBean jaxWsProxyFactory = new JaxWsProxyFactoryBean();
        jaxWsProxyFactory.setTransportId(TRANSPORT_IDENTIFIER);
        jaxWsProxyFactory.setServiceClass(XDSDocumentService.class);
        jaxWsProxyFactory.setServiceName(new QName("urn:ihe:iti:xds-b:2007", "XDSDocumentService"));
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/latest/#A_15186
        jaxWsProxyFactory.getFeatures().add(new WSAddressingFeature());
        jaxWsProxyFactory.setAddress(address);
        Map<String, Object> props = new HashMap<String, Object>();
        // Boolean.TRUE or "true" will work as the property value below
        props.put("mtom-enabled", Boolean.TRUE);
        jaxWsProxyFactory.setProperties(props);
        jaxWsProxyFactory.setBindingId(SOAPBinding.SOAP12HTTP_BINDING);

        jaxWsProxyFactory.getOutInterceptors().addAll(
            List.of(
                new LoggingOutInterceptor(),
                new CxfVauSetupInterceptor(vauFacade, vauConfig, epaUserAgent),
                new CxfVauWriteSoapInterceptor(vauFacade)
            )
        );
        jaxWsProxyFactory.getInInterceptors().addAll(
            List.of(new LoggingInInterceptor(), new CxfVauReadSoapInterceptor(vauFacade))
        );
        T portType = jaxWsProxyFactory.create(clazz);
        Client client = ClientProxy.getClient(portType);
        initConduit((HTTPConduit) client.getConduit(), vauConfig.getConnectionTimeoutMs());
        return portType;
    }
}
