package de.service.health.api.epa4all;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.service.health.api.epa4all.proxy.FhirProxyService;
import de.service.health.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.StubMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderStubClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import de.servicehealth.feature.FeatureConfig;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.http.client.fluent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.utils.ServerUtils.getBaseUrl;

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
    private final FeatureConfig featureConfig;
    private final ClientFactory clientFactory;
    private final Instance<VauFacade> vauFacadeInstance;
    private final EpaServicePortProvider epaServicePortProvider;

    private final Cache<String, EpaAPI> xInsurantid2ePAApi = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    @Inject
    public EpaMultiService(
        VauConfig vauConfig,
        EpaConfig epaConfig,
        FeatureConfig featureConfig,
        ClientFactory clientFactory,
        Instance<VauFacade> vauFacadeInstance,
        EpaServicePortProvider epaServicePortProvider
    ) {
        this.epaServicePortProvider = epaServicePortProvider;
        this.vauFacadeInstance = vauFacadeInstance;
        this.clientFactory = clientFactory;
        this.featureConfig = featureConfig;
        this.epaConfig = epaConfig;
        this.vauConfig = vauConfig;
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

                    String epaUserAgent = epaConfig.getEpaUserAgent();

                    String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = epaServicePortProvider.getDocumentManagementInsurantPortType(
                        documentManagementInsurantUrl, epaUserAgent, vauFacade, vauConfig
                    );

                    AccountInformationApi accountInformationApi = clientFactory.createPlainClient(
                        AccountInformationApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    AuthorizationSmcBApi authorizationSmcBApi = createProxyClient(
                        AuthorizationSmcBApi.class, backend, epaConfig.getAuthorizationServiceUrl(), vauFacade
                    );
                    EntitlementsApi entitlementsApi = createProxyClient(
                        EntitlementsApi.class, backend, epaConfig.getEntitlementServiceUrl(), vauFacade
                    );

                    IFhirProxy fhirProxy = new FhirProxyService(backend, epaConfig, vauConfig, vauFacade);

                    IMedicationClient medicationClient;
                    IRenderClient renderClient;
                    if (featureConfig.isNativeFhirEnabled()) {
                        FhirContext fhirContext = FhirContext.forR4();

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
            return epaServicePortProvider.getDocumentManagementPortType(
                documentManagementUrl, epaConfig.getEpaUserAgent(), vauFacade, vauConfig
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create IDocumentManagementPortType", e);
        }
    }

    private <T> T createProxyClient(Class<T> clazz, String backend, String serviceUrl, VauFacade vauFacade) throws Exception {
        return clientFactory.createProxyClient(vauFacade, epaConfig.getEpaUserAgent(), clazz, getBackendUrl(backend, serviceUrl));
    }

    public EpaAPI getEpaAPI(String insurantId) {
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

    private boolean hasEpaRecord(EpaAPI api, String xInsurantid) {
        boolean result = false;
        try {
            api.getAccountInformationApi().getRecordStatus(xInsurantid, epaConfig.getEpaUserAgent());
            result = true;
        } catch (Exception e) {
            log.info(String.format(
                "ePA backend [%s] doesn't contain ePA record for %s: %s", api.getBackend(), xInsurantid, e.getMessage())
            );
        }
        return result;
    }
}