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
import de.servicehealth.epa4all.medication.fhir.restful.extension.GenericDirectClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.Getter;
import org.apache.http.client.fluent.Executor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.ServerUtils.getBackendUrl;
import static de.servicehealth.utils.ServerUtils.getBaseUrl;

@ApplicationScoped
@Startup
public class EpaMultiService extends StartableService {

    private static final Logger log = Logger.getLogger(EpaMultiService.class.getName());

    @Getter
    private final ConcurrentHashMap<String, EpaAPI> epaBackendMap = new ConcurrentHashMap<>();

    @Getter
    private final VauConfig vauConfig;

    @Getter
    private final EpaConfig epaConfig;
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
        ClientFactory clientFactory,
        Instance<VauFacade> vauFacadeInstance,
        EpaServicePortProvider epaServicePortProvider
    ) {
        this.epaServicePortProvider = epaServicePortProvider;
        this.vauFacadeInstance = vauFacadeInstance;
        this.clientFactory = clientFactory;
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

                    String documentManagementUrl = getBackendUrl(backend, epaConfig.getDocumentManagementServiceUrl());
                    IDocumentManagementPortType documentManagementPortType = epaServicePortProvider.getDocumentManagementPortType(
                        documentManagementUrl, epaUserAgent, vauFacade
                    );

                    String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = epaServicePortProvider.getDocumentManagementInsurantPortType(
                        documentManagementInsurantUrl, epaUserAgent, vauFacade
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

                    GenericDirectClient medicationClient = null;
                    VauRenderClient renderClient = null;
                    if (vauConfig.isInternalFhirEnabled()) {
                        VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(FhirContext.forR4());
                        String medicationApiUrl = getBackendUrl(backend, epaConfig.getMedicationServiceApiUrl());
                        apiClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationApiUrl));
                        medicationClient = apiClientFactory.newGenericClient(medicationApiUrl.replace("+vau", ""));

                        String medicationRenderUrl = getBackendUrl(backend, epaConfig.getMedicationServiceRenderUrl());
                        VauRestfulClientFactory renderClientFactory = new VauRestfulClientFactory(FhirContext.forR4());
                        renderClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationRenderUrl));
                        Executor renderExecutor = Executor.newInstance(renderClientFactory.getVauHttpClient());
                        renderClient = new VauRenderClient(
                            renderExecutor, epaUserAgent, medicationRenderUrl.replace("+vau", "")
                        );
                    }

                    return new EpaAPIAggregator(
                        backend,
                        vauFacade,
                        renderClient,
                        medicationClient,
                        documentManagementPortType,
                        documentManagementInsurantPortType,
                        accountInformationApi,
                        authorizationSmcBApi,
                        entitlementsApi,
                        fhirProxy
                    );
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Error while instantiating EPA API", e);
                    throw new RuntimeException(e);
                }
            }));
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
            throw new WebApplicationException(String.format("Insurant [%s] - ePA record is not found", insurantId));
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