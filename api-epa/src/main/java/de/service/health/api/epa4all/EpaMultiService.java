package de.service.health.api.epa4all;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.service.health.api.epa4all.annotation.EpaRestFeatures;
import de.service.health.api.epa4all.annotation.EpaSoapFeatures;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.service.health.api.epa4all.proxy.AdminProxyService;
import de.service.health.api.epa4all.proxy.FhirProxyService;
import de.service.health.api.epa4all.proxy.IAdminProxy;
import de.service.health.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauReadSoapInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauSetupInterceptor;
import de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteSoapInterceptor;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.StubMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderStubClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import de.servicehealth.feature.EpaFeatureConfig;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.XDSDocumentService;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.http.client.fluent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initConduit;
import static de.servicehealth.epa4all.cxf.transport.HTTPVauTransportFactory.TRANSPORT_IDENTIFIER;
import static de.servicehealth.logging.LogContext.resultMdc;
import static de.servicehealth.logging.LogField.BACKEND;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.utils.ServerUtils.getBaseUrl;
import static jakarta.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING;
import static java.lang.Boolean.TRUE;
import static org.apache.cxf.message.Message.MTOM_ENABLED;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
@Startup
public class EpaMultiService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(EpaMultiService.class.getName());

    // TODO: indirectly used in the Retrier lib-cetp
    public static final String EPA_RECORD_IS_NOT_FOUND = "ePA record is not found";

    @Getter
    private final ConcurrentHashMap<String, EpaAPI> epaBackendMap = new ConcurrentHashMap<>();

    @Getter
    private final VauConfig vauConfig;

    @Getter
    private final EpaConfig epaConfig;
    private final EpaFeatureConfig featureConfig;
    private final ClientFactory clientFactory;
    private final Instance<VauFacade> vauFacadeInstance;
    private final ServicehealthConfig servicehealthConfig;
    private final List<Feature> epaRestFeatures;
    private final List<Feature> epaSoapFeatures;

    private final Cache<String, EpaAPI> xInsurantid2ePAApi = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    @Inject
    public EpaMultiService(
        VauConfig vauConfig,
        EpaConfig epaConfig,
        ClientFactory clientFactory,
        EpaFeatureConfig featureConfig,
        Instance<VauFacade> vauFacadeInstance,
        ServicehealthConfig servicehealthConfig,
        @EpaRestFeatures List<Feature> epaRestFeatures,
        @EpaSoapFeatures List<Feature> epaSoapFeatures
    ) {
        this.vauFacadeInstance = vauFacadeInstance;
        this.servicehealthConfig = servicehealthConfig;
        this.clientFactory = clientFactory;
        this.featureConfig = featureConfig;
        this.epaConfig = epaConfig;
        this.vauConfig = vauConfig;
        this.epaRestFeatures = epaRestFeatures;
        this.epaSoapFeatures = epaSoapFeatures;
    }

    @Override
    public int getPriority() {
        return MultiEpaPriority;
    }

    @Override
    public void onStart() {
        epaConfig.getEpaBackends().forEach(backend ->
            epaBackendMap.computeIfAbsent(backend, k -> {
                try {
                    VauFacade vauFacade = vauFacadeInstance.get();
                    vauFacade.setBackend(backend);

                    String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = createXDSDocumentPortType(
                        documentManagementInsurantUrl,
                        IDocumentManagementInsurantPortType.class,
                        vauFacade
                    );

                    AccountInformationApi accountInformationApi = clientFactory.createRestPlainClient(
                        AccountInformationApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    String authorizationServiceUrl = epaConfig.getAuthorizationServiceUrl();
                    AuthorizationSmcBApi authorizationSmcBApi = createProxyClient(
                        AuthorizationSmcBApi.class, backend, authorizationServiceUrl, vauFacade
                    );
                    String entitlementServiceUrl = epaConfig.getEntitlementServiceUrl();
                    EntitlementsApi entitlementsApi = createProxyClient(
                        EntitlementsApi.class, backend, entitlementServiceUrl, vauFacade
                    );

                    Set<String> maskedHeaders = servicehealthConfig.getMaskedHeaders();
                    Set<String> maskedAttributes = servicehealthConfig.getMaskedAttributes();
                    IFhirProxy fhirProxy = new FhirProxyService(
                        backend, epaConfig, vauConfig, vauFacade, maskedHeaders, maskedAttributes, epaRestFeatures
                    );
                    IAdminProxy adminProxy = new AdminProxyService(
                        backend, epaConfig, vauConfig, vauFacade, maskedHeaders, maskedAttributes
                    );

                    IMedicationClient medicationClient;
                    IRenderClient renderClient;
                    if (featureConfig.isNativeFhirEnabled()) {
                        FhirContext fhirContext = FhirContext.forR4();
                        String epaUserAgent = epaConfig.getEpaUserAgent();
                        
                        VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(fhirContext);
                        String medicationApiUrl = getBackendUrl(backend, epaConfig.getMedicationServiceApiUrl());
                        apiClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationApiUrl));
                        medicationClient = apiClientFactory.newGenericClient(medicationApiUrl.replace("+vau", ""));

                        String medicationRenderUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderUrl());
                        VauRestfulClientFactory renderClientFactory = new VauRestfulClientFactory(fhirContext);
                        renderClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationRenderUrl));
                        Executor renderExecutor = Executor.newInstance(renderClientFactory.getVauHttpClient());
                        renderClient = new VauRenderClient(
                            renderExecutor, epaUserAgent, medicationRenderUrl.replace("+vau", "")
                        );
                    } else {
                        medicationClient = new StubMedicationClient();
                        renderClient = new VauRenderStubClient();
                    }

                    return new EpaAPIAggregator(
                        backend,
                        vauFacade,
                        renderClient,
                        medicationClient,
                        () -> buildIDocumentManagementPortType(backend, vauFacade),
                        documentManagementInsurantPortType,
                        accountInformationApi,
                        authorizationSmcBApi,
                        entitlementsApi,
                        adminProxy,
                        fhirProxy
                    );
                } catch (Exception e) {
                    log.error("Error while instantiating EPA API", e);
                    throw new RuntimeException(e);
                }
            }));
    }

    private IDocumentManagementPortType buildIDocumentManagementPortType(String backend, VauFacade vauFacade) {
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

    private <T> T createProxyClient(
        Class<T> clazz,
        String backend,
        String serviceUrl,
        VauFacade vauFacade
    ) throws Exception {
        String backendUrl = getBackendUrl(backend, serviceUrl);
        Set<String> maskedHeaders = servicehealthConfig.getMaskedHeaders();
        Set<String> maskedAttributes = servicehealthConfig.getMaskedAttributes();
        return clientFactory.createRestProxyClient(
            vauFacade, clazz, backendUrl, maskedHeaders, maskedAttributes, epaRestFeatures
        );
    }

    public EpaAPI getEpaAPI(String backend) {
        return backend == null ? null : epaBackendMap.get(backend);
    }

    public EpaAPI findEpaAPI(String insurantId) {
        EpaAPI epaAPI = xInsurantid2ePAApi.getIfPresent(insurantId);
        if (epaAPI != null) {
            return epaAPI;
        } else {
            for (EpaAPI api : epaBackendMap.values()) {
                if (hasEpaRecord(api, insurantId)) {
                    xInsurantid2ePAApi.put(insurantId, api);
                    return api;
                }
            }
            String msg = String.format("Insurant [%s] - %s in ePA backends", insurantId, EPA_RECORD_IS_NOT_FOUND);
            throw new IllegalStateException(msg);
        }
    }

    private boolean hasEpaRecord(EpaAPI api, String insurantId) {
        return resultMdc(Map.of(INSURANT, insurantId, BACKEND, api.getBackend()), () -> {
            boolean result = false;
            try {
                api.getAccountInformationApi().getRecordStatus(insurantId, epaConfig.getEpaUserAgent());
                result = true;
            } catch (Exception e) {
                log.info(String.format("ePA record not found: %s", e.getMessage()));
            }
            return result;
        });
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

        Set<String> maskedHeaders = servicehealthConfig.getMaskedHeaders();
        Set<String> maskedAttributes = servicehealthConfig.getMaskedAttributes();

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