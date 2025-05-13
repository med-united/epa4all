package de.servicehealth.api.epa4all;

import de.servicehealth.api.epa4all.annotation.EpaSoapFeatures;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadSoapInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteSoapInterceptor;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initConduit;
import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static jakarta.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING;
import static java.lang.Boolean.TRUE;
import static org.apache.cxf.message.Message.MTOM_ENABLED;

@ApplicationScoped
public class IDocumentManagementPortTypeProvider {

    @Inject
    VauConfig vauConfig;

    @Inject
    EpaConfig epaConfig;

    @Inject
    @EpaSoapFeatures
    List<Feature> epaSoapFeatures;

    @Inject
    ServicehealthConfig servicehealthConfig;


    public IDocumentManagementInsurantPortType buildIDocumentManagementInsurantPortType(
        String backend,
        VauFacade vauFacade
    ) throws Exception {
        String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
        return createXDSDocumentPortType(
            documentManagementInsurantUrl,
            IDocumentManagementInsurantPortType.class,
            vauFacade
        );
    }

    public IDocumentManagementPortType buildIDocumentManagementPortType(String backend, VauFacade vauFacade) {
        try {
            String documentManagementUrl = getBackendUrl(backend, epaConfig.getDocumentManagementServiceUrl());
            return createXDSDocumentPortType(
                documentManagementUrl,
                IDocumentManagementPortType.class,
                vauFacade
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create IDocumentManagementPortType", e);
        }
    }

    private <T> T createXDSDocumentPortType(
        String address,
        Class<T> clazz,
        VauFacade vauFacade
    ) throws Exception {
        JaxWsProxyFactoryBean soapProxyFactory = new JaxWsProxyFactoryBean();
        soapProxyFactory.setTransportId(TRANSPORT_IDENTIFIER);
        soapProxyFactory.setServiceClass(XDSDocumentService.class);
        soapProxyFactory.setServiceName(new QName("urn:ihe:iti:xds-b:2007", "XDSDocumentService"));
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/latest/#A_15186

        soapProxyFactory.getFeatures().addAll(epaSoapFeatures);
        soapProxyFactory.setAddress(address);
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(MTOM_ENABLED, TRUE);
        soapProxyFactory.setProperties(props);
        soapProxyFactory.setBindingId(SOAP12HTTP_BINDING);

        Set<String> maskedHeaders = servicehealthConfig.getSafeMaskedHeaders();
        Set<String> maskedAttributes = servicehealthConfig.getSafeMaskedAttributes();

        soapProxyFactory.getOutInterceptors().addAll(
            List.of(
                new LoggingOutInterceptor(),
                new CxfVauSetupInterceptor(vauFacade),
                new CxfVauWriteSoapInterceptor(vauFacade, maskedHeaders, maskedAttributes)
            )
        );
        soapProxyFactory.getInInterceptors().addAll(
            List.of(new LoggingInInterceptor(), new CxfVauReadSoapInterceptor(vauFacade))
        );
        T portType = soapProxyFactory.create(clazz);
        Client client = ClientProxy.getClient(portType);
        initConduit((HTTPConduit) client.getConduit(), vauConfig.getConnectionTimeoutMs());
        return portType;
    }
}