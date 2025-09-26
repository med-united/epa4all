package de.servicehealth.api.epa4all;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.ConsentDecisionsApi;
import de.servicehealth.api.epa4all.annotation.EpaRestFeatures;
import de.servicehealth.api.epa4all.authorization.AuthorizationSmcbAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsFdvAPI;
import de.servicehealth.api.epa4all.jmx.EpaMXBeanRegistry;
import de.servicehealth.api.epa4all.proxy.AdminProxyService;
import de.servicehealth.api.epa4all.proxy.FhirProxyService;
import de.servicehealth.api.epa4all.proxy.IAdminProxy;
import de.servicehealth.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import lombok.Getter;
import org.apache.cxf.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpConnectTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.logging.LogContext.resultMdc;
import static de.servicehealth.logging.LogField.BACKEND;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.utils.ServerUtils.getBackendUrl;

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
    private final EpaMXBeanRegistry epaMXBeanRegistry;
    private final ClientFactory clientFactory;
    private final Instance<VauFacade> vauFacadeInstance;
    private final ServicehealthConfig servicehealthConfig;
    private final List<Feature> epaRestFeatures;
    private final IDocumentManagementPortTypeProvider portTypeProvider;

    private final Cache<String, EpaAPI> apiCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    @Inject
    public EpaMultiService(
        VauConfig vauConfig,
        EpaConfig epaConfig,
        ClientFactory clientFactory,
        EpaMXBeanRegistry epaMXBeanRegistry,
        Instance<VauFacade> vauFacadeInstance,
        ServicehealthConfig servicehealthConfig,
        @EpaRestFeatures List<Feature> epaRestFeatures,
        IDocumentManagementPortTypeProvider portTypeProvider
    ) {
        this.vauFacadeInstance = vauFacadeInstance;
        this.servicehealthConfig = servicehealthConfig;
        this.clientFactory = clientFactory;
        this.epaMXBeanRegistry = epaMXBeanRegistry;
        this.epaConfig = epaConfig;
        this.vauConfig = vauConfig;
        this.epaRestFeatures = epaRestFeatures;
        this.portTypeProvider = portTypeProvider;
    }

    @Override
    public int getPriority() {
        return MultiEpaPriority;
    }

    @Override
    public void doStart() {
        epaConfig.getEpaBackends().forEach(backend ->
            epaBackendMap.computeIfAbsent(backend, k -> {
                try {
                    VauFacade vauFacade = vauFacadeInstance.get();
                    vauFacade.setBackend(backend);

                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = portTypeProvider
                        .buildIDocumentManagementInsurantPortType(backend, vauFacade);

                    AccountInformationApi accountInformationApi = clientFactory.createRestPlainClient(
                        AccountInformationApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    ConsentDecisionsApi consentDecisionsApi = clientFactory.createRestPlainClient(
                        ConsentDecisionsApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    String authorizationServiceUrl = epaConfig.getAuthorizationServiceUrl();
                    AuthorizationSmcbAPI authorizationSmcBApi = createProxyClient(
                        AuthorizationSmcbAPI.class, backend, authorizationServiceUrl, vauFacade
                    );
                    String entitlementServiceUrl = epaConfig.getEntitlementServiceUrl();
                    EntitlementsAPI entitlementsApi = createProxyClient(
                        EntitlementsAPI.class, backend, entitlementServiceUrl, vauFacade
                    );
                    EntitlementsFdvAPI entitlementsFdvApi = createProxyClient(
                        EntitlementsFdvAPI.class, backend, entitlementServiceUrl, vauFacade
                    );

                    Set<String> maskedHeaders = servicehealthConfig.getSafeMaskedHeaders();
                    Set<String> maskedAttributes = servicehealthConfig.getSafeMaskedAttributes();
                    IFhirProxy fhirProxy = new FhirProxyService(
                        backend, epaConfig, vauConfig, vauFacade, epaMXBeanRegistry, maskedHeaders, maskedAttributes, epaRestFeatures
                    );
                    IAdminProxy adminProxy = new AdminProxyService(
                        backend, epaConfig, vauConfig, vauFacade, maskedHeaders, maskedAttributes
                    );

                    return new EpaAPIAggregator(
                        backend,
                        vauFacade,
                        () -> buildIDocumentManagementPortType(backend, vauFacade),
                        documentManagementInsurantPortType,
                        accountInformationApi,
                        consentDecisionsApi,
                        authorizationSmcBApi,
                        entitlementsFdvApi,
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
            return portTypeProvider.buildIDocumentManagementPortType(backend, vauFacade);
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
        Set<String> maskedHeaders = servicehealthConfig.getSafeMaskedHeaders();
        Set<String> maskedAttributes = servicehealthConfig.getSafeMaskedAttributes();
        return clientFactory.createRestProxyClient(
            vauFacade, clazz, backendUrl, maskedHeaders, maskedAttributes, epaRestFeatures
        );
    }

    public boolean checkInsurantEPA(String insurantId) {
        for (EpaAPI api : epaBackendMap.values()) {
            AccountInformationApi accountInformationApi = api.getAccountInformationAPI();
            try {
                accountInformationApi.getRecordStatus(insurantId, epaConfig.getEpaUserAgent());
                return true;
            } catch (ProcessingException e) {
                if (e.getCause() instanceof HttpConnectTimeoutException) {
                    throw e;
                }
            } catch (BadRequestException e) {
                throw new BadRequestException("[%s] Check ePA failed: 400 BadRequest".formatted(insurantId));
            } catch (NotFoundException e) {
                return false;
            }
        }
        return false;
    }

    public EpaAPI findEpaAPI(String insurantId) throws EpaNotFoundException {
        EpaAPI epaAPI = apiCache.getIfPresent(insurantId);
        if (epaAPI != null) {
            return epaAPI;
        } else {
            for (EpaAPI api : epaBackendMap.values()) {
                if (hasEpaRecord(api, insurantId)) {
                    apiCache.put(insurantId, api);
                    return api;
                }
            }
            String msg = String.format("Insurant [%s] - %s in ePA backends", insurantId, EPA_RECORD_IS_NOT_FOUND);
            throw new EpaNotFoundException(msg, insurantId);
        }
    }

    private boolean hasEpaRecord(EpaAPI api, String insurantId) {
        return resultMdc(Map.of(INSURANT, insurantId, BACKEND, api.getBackend()), () -> {
            boolean result = false;
            try {
                api.getAccountInformationAPI().getRecordStatus(insurantId, epaConfig.getEpaUserAgent());
                result = true;
            } catch (Exception e) {
                log.info(String.format("ePA record not found: %s", e.getMessage()));
            }
            return result;
        });
    }

    public void invalidateApiCache() {
        apiCache.invalidateAll();
    }
}