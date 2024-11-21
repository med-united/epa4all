package de.servicehealth.epa4all.server.rest;

import de.health.service.check.HealthChecker;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.api.EntitlementsApi;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.VAU_NP;
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
        Instant expirationTime = insuranceDataService.getEntitlementExpirationTime(telematikId, insurantId);
        if (expirationTime == null || expirationTime.isBefore(Instant.now())) {
            Instant entitlementValidTo = setEntitlement(userRuntimeConfig, insuranceData, epaAPI, smcbHandle);
            insuranceDataService.updateEntitlement(entitlementValidTo, telematikId, insurantId);
        }
        return new EpaContext(insuranceData, prepareRuntimeAttributes(
            insuranceData.getInsurantId(),
            userRuntimeConfig.getConnectorBaseURL(),
            epaAPI.getBackend())
        );
    }

    private Map<String, Object> prepareRuntimeAttributes(String insurantId, String konnektor, String backend) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, multiEpaService.getEpaConfig().getUserAgent());
        String vauNp = vauNpProvider.getVauNp(konnektor, backend);
        if (vauNp != null) {
            attributes.put(VAU_NP, vauNp);
        }
        return attributes;
    }

    private Instant setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String smcbHandle
    ) {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        String entitlementPSJWT = idpClient.createEntitlementPSJWT(
            smcbHandle, pz, userRuntimeConfig, epaAPI.getAuthorizationSmcBApi()
        );
        entitlementRequest.setJwt(entitlementPSJWT);
        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        ValidToResponseType response = entitlementsApi.setEntitlementPs(
            insurantId,
            multiEpaService.getEpaConfig().getUserAgent(),
            entitlementRequest
        );
        if (response.getValidTo() != null) {
            return Instant.ofEpochMilli(response.getValidTo().getTime());
        } else {
            log.warning("response.getValidTo() is null. Returning now.");
            return Instant.now();
        }
    }
}
