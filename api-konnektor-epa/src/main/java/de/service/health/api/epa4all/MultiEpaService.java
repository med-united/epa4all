package de.service.health.api.epa4all;

import ca.uhn.fhir.context.FhirContext;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.cxf.VauClientFactory;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import de.servicehealth.vau.VauClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.http.client.fluent.Executor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static de.servicehealth.utils.URLUtils.getBaseUrl;

@ApplicationScoped
@Startup
public class MultiEpaService {

    private static final Logger log = Logger.getLogger(MultiEpaService.class.getName());

    private final static String USER_AGENT = "CLIENTID1234567890AB/2.1.12-45";

    @Getter
    private final ConcurrentHashMap<String, EpaAPI> epaBackendMap = new ConcurrentHashMap<>();

    private final VauClient vauClient;

    @Inject
    public MultiEpaService(
        EpaConfig epaConfig,
        VauClientFactory vauClientFactory,
        EServicePortProvider eServicePortProvider
    ) {
        this.vauClient = vauClientFactory.getVauClient();
        
        epaConfig.getEpaBackends().forEach(backend ->
            epaBackendMap.computeIfAbsent(backend, k -> {
                try {
                    String documentManagementUrl = getBackendUrl(backend, epaConfig.getDocumentManagementServiceUrl());
                    IDocumentManagementPortType documentManagementPortType = eServicePortProvider.getDocumentManagementPortType(documentManagementUrl);

                    String documentManagementInsurantUrl = getBackendUrl(backend, epaConfig.getDocumentManagementInsurantServiceUrl());
                    IDocumentManagementInsurantPortType documentManagementInsurantPortType = eServicePortProvider.getDocumentManagementInsurantPortType(documentManagementInsurantUrl);

                    AccountInformationApi accountInformationApi = ClientFactory.createPlainClient(
                        AccountInformationApi.class, getBackendUrl(backend, epaConfig.getInformationServiceUrl())
                    );
                    AuthorizationSmcBApi authorizationSmcBApi = createProxyClient(
                        AuthorizationSmcBApi.class, backend, epaConfig.getAuthorizationServiceUrl()
                    );
                    EntitlementsApi entitlementsApi = createProxyClient(
                        EntitlementsApi.class, backend, epaConfig.getEntitlementServiceUrl()
                    );

                    FhirContext ctx = FhirContext.forR4();
                    String medicationServiceApiUrl = epaConfig.getMedicationServiceApiUrl();
                    String backendUrl = getBackendUrl(backend, medicationServiceApiUrl);
                    VauRestfulClientFactory.applyToFhirContext(ctx, vauClient, getBaseUrl(backendUrl));
                    IMedicationClient medicationClient = ctx.newRestfulClient(IMedicationClient.class, backendUrl);

                    ctx = FhirContext.forR4();
                    String medicationServiceRenderUrl = epaConfig.getMedicationServiceRenderUrl();
                    backendUrl = getBackendUrl(backend, medicationServiceRenderUrl);
                    Executor executor = VauRestfulClientFactory.applyToFhirContext(ctx, vauClient, getBaseUrl(backendUrl));
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

    private <T> T createProxyClient(Class<T> clazz, String backend, String serviceUrl) throws Exception {
        return ClientFactory.createProxyClient(vauClient, clazz, getBackendUrl(backend, serviceUrl));
    }

    private String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
    }

    public EpaAPI getEpaAPI(String xInsurantid) {
        for (EpaAPI api : epaBackendMap.values()) {
            if (hasEpaRecord(api, xInsurantid)) {
                return api;
            }
        }
        return null;
    }

    private boolean hasEpaRecord(EpaAPI api, String xInsurantid) {
        boolean result = false;
        try {
            api.getAccountInformationApi().getRecordStatus(xInsurantid, USER_AGENT);
            result = true;
        } catch (Exception e) {
            log.info(String.format(
                "ePA backend [%s] doesn't contain ePA record for %s: %s", api.getBackend(), xInsurantid, e.getMessage())
            );
        }
        return result;
    }
}