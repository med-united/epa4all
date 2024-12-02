package de.servicehealth.epa4all.server.rest;

import de.health.service.check.HealthChecker;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.epa4all.server.bulk.BulkTransfer;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public abstract class AbstractResource {

    private static final Logger log = Logger.getLogger(AbstractResource.class.getName());

    @Inject
    Instance<XDSDocumentService> xdsDocumentService;

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    EpaFileDownloader epaFileDownloader;

    @Inject
    Event<FileUpload> eventFileUpload;

    @Inject
    MultiEpaService multiEpaService;

    @Inject
    HealthChecker healthChecker;

    @Inject
    VauNpProvider vauNpProvider;

    @Inject
    BulkTransfer bulkTransfer;

    @Inject
    IdpClient idpClient;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TelematikId
    String telematikId;

    @Inject
    @SMCBHandle
    String smcbHandle;

    protected EpaContext prepareEpaContext(String kvnr) throws Exception {
        InsuranceData insuranceData = insuranceDataService.getInsuranceDataOrReadVSD(
            telematikId, kvnr, smcbHandle, userRuntimeConfig
        );
        String insurantId = insuranceData.getInsurantId();
        EpaAPI epaAPI = multiEpaService.getEpaAPI(insurantId);
        String userAgent = multiEpaService.getEpaConfig().getUserAgent();

        // TODO resolve
        resolveEntitlement(insuranceData, epaAPI, userAgent);
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        Map<String, Object> xHeaders = prepareXHeaders(
            insurantId, userAgent, konnektorUrl, epaAPI.getBackend()
        );
        return new EpaContext(insuranceData, xHeaders);
    }

    private Map<String, Object> prepareXHeaders(
        String insurantId,
        String userAgent,
        String konnektorUrl,
        String backend
    ) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, userAgent);
        attributes.put(X_BACKEND, backend);
        String vauNp = vauNpProvider.getVauNp(konnektorUrl, backend);
        if (vauNp != null) {
            attributes.put(VAU_NP, vauNp);
        }
        return attributes;
    }

    private void resolveEntitlement(InsuranceData insuranceData, EpaAPI epaAPI, String userAgent) throws IOException {
        String insurantId = insuranceData.getInsurantId();
        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, insurantId);
        if (validTo == null || validTo.isBefore(Instant.now())) {
            setEntitlement(userRuntimeConfig, insuranceData, epaAPI, userAgent, smcbHandle);
        } else {
            log.info(String.format("[%s/%s] Entitlement is valid until %s", telematikId, insurantId, validTo));
        }
    }

    private void setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String userAgent,
        String smcbHandle
    ) throws IOException {
        String pz = insuranceData.getPz();
        String backend = epaAPI.getBackend();
        AuthorizationSmcBApi authorizationSmcBApi = epaAPI.getAuthorizationSmcBApi();
        String jwt = idpClient.createEntitlementPSJWT(backend, smcbHandle, pz, userRuntimeConfig, authorizationSmcBApi);

        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        entitlementRequest.setJwt(jwt);
        
        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        String insurantId = insuranceData.getInsurantId();
        ValidToResponseType response = entitlementsApi.setEntitlementPs(insurantId, userAgent, backend, entitlementRequest);
        if (response.getValidTo() != null) {
            insuranceDataService.updateEntitlement(response.getValidTo().toInstant(), telematikId, insurantId);
        }
    }
}
