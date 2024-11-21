package de.service.health.api.epa4all;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import de.servicehealth.startup.StartableService;
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
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.CXF_CLIENT_FACTORY_STARTUP_PRIORITY;
import static de.servicehealth.utils.ServerUtils.getBaseUrl;

@ApplicationScoped
@Startup
public class MultiEpaService extends StartableService {

    private static final Logger log = Logger.getLogger(MultiEpaService.class.getName());

    @Getter
    private final ConcurrentHashMap<String, EpaAPI> epaBackendMap = new ConcurrentHashMap<>();

    @Getter
    private final EpaConfig epaConfig;
    private final ClientFactory clientFactory;
    private final Instance<VauFacade> vauFacadeInstance;
    private final EServicePortProvider eServicePortProvider;

    private final Cache<String, EpaAPI> xInsurantid2ePAApi = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    @Inject
    public MultiEpaService(
        EpaConfig epaConfig,
        ClientFactory clientFactory,
        Instance<VauFacade> vauFacadeInstance,
        EServicePortProvider eServicePortProvider
    ) {
        this.eServicePortProvider = eServicePortProvider;
        this.vauFacadeInstance = vauFacadeInstance;
        this.clientFactory = clientFactory;
        this.epaConfig = epaConfig;
    }

    @Override
    public int getPriority() {
        return MULTI_EPA_STARTUP_PRIORITY;
    }

    @Override
    protected void onStart() {
        epaConfig.getEpaBackends().forEach(backend ->
            epaBackendMap.computeIfAbsent(backend, k -> {
                try {
                    VauFacade vauFacade = vauFacadeInstance.get();
                    vauFacade.setBackend(backend);

                    String documentManagementUrl = getBackendUrl(backend, epaConfig.getDocumentManagementServiceUrl());
                    IDocumentManagementPortType documentManagementPortType = eServicePortProvider.getDocumentManagementPortType(documentManagementUrl, vauFacade);

                    String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = eServicePortProvider.getDocumentManagementInsurantPortType(documentManagementInsurantUrl, vauFacade);

                    AccountInformationApi accountInformationApi = clientFactory.createPlainClient(
                        AccountInformationApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    AuthorizationSmcBApi authorizationSmcBApi = createProxyClient(
                        AuthorizationSmcBApi.class, backend, epaConfig.getAuthorizationServiceUrl(), vauFacade
                    );
                    EntitlementsApi entitlementsApi = createProxyClient(
                        EntitlementsApi.class, backend, epaConfig.getEntitlementServiceUrl(), vauFacade
                    );

                    FhirContext ctx = FhirContext.forR4();
                    String medicationServiceApiUrl = epaConfig.getMedicationServiceApiUrl();
                    String backendUrl = getBackendUrl(backend, medicationServiceApiUrl);
                    VauRestfulClientFactory.applyToFhirContext(ctx, vauFacade, getBaseUrl(backendUrl));
                    IMedicationClient medicationClient = ctx.newRestfulClient(IMedicationClient.class, backendUrl);

                    ctx = FhirContext.forR4();
                    String medicationServiceRenderUrl = epaConfig.getMedicationServiceRenderUrl();
                    backendUrl = getBackendUrl(backend, medicationServiceRenderUrl);
                    Executor executor = VauRestfulClientFactory.applyToFhirContext(ctx, vauFacade, getBaseUrl(backendUrl));
                    IRenderClient renderClient = new VauRenderClient(executor, backendUrl);

                    return new EpaAPIAggregator(
                        backend,
                        documentManagementPortType,
                        documentManagementInsurantPortType,
                        accountInformationApi,
                        authorizationSmcBApi,
                        entitlementsApi,
                        medicationClient,
                        renderClient
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
    }

    private <T> T createProxyClient(Class<T> clazz, String backend, String serviceUrl, VauFacade vauFacade) throws Exception {
        return clientFactory.createProxyClient(vauFacade, clazz, getBackendUrl(backend, serviceUrl));
    }

    private String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
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
            api.getAccountInformationApi().getRecordStatus(xInsurantid, epaConfig.getUserAgent());
            result = true;
        } catch (Exception e) {
            log.info(String.format(
                "ePA backend [%s] doesn't contain ePA record for %s: %s", api.getBackend(), xInsurantid, e.getMessage())
            );
        }
        return result;
    }
}