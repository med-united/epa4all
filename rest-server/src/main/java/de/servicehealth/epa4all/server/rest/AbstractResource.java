package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.server.bulk.BulkUploader;
import de.servicehealth.epa4all.server.bulk.FileUploader;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.xdsdocument.HttpXDSDocumentService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.apache.cxf.jaxrs.client.WebClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public abstract class AbstractResource {

    private static final Logger log = Logger.getLogger(AbstractResource.class.getName());

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    Instance<HttpXDSDocumentService> xdsDocumentService;

    @Inject
    MultiEpaService multiEpaService;

    @Inject
    EpaFileTracker epaFileTracker;

    @Inject
    FileUploader fileUploader;

    @Inject
    BulkUploader bulkUploader;

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
        String np = idpClient.getVauNpSync(userRuntimeConfig, insurantId, smcbHandle); // TODO verify is it needed further
        if (expirationTime == null || expirationTime.isBefore(Instant.now())) {
            Instant entitlementValidTo = setEntitlement(userRuntimeConfig, insuranceData, epaAPI, smcbHandle, np);
            insuranceDataService.updateEntitlement(entitlementValidTo, telematikId, insurantId);
        }
        return new EpaContext(insuranceData, prepareRuntimeAttributes(insuranceData, np));
    }

    private Map<String, Object> prepareRuntimeAttributes(InsuranceData insuranceData, String np) {
        Map<String, Object> attributes = new HashMap<>();
        String insurantId = insuranceData.getInsurantId();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, USER_AGENT);
        attributes.put(VAU_NP, np); // TODO check if it is needed
        return attributes;
    }

    private Instant setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String smcbHandle,
        String np
    ) throws Exception {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        String entitlementPSJWT = idpClient.createEntitlementPSJWT(smcbHandle, insurantId, pz, userRuntimeConfig);
        entitlementRequest.setJwt(entitlementPSJWT);
        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        WebClient.getConfig(entitlementsApi).getRequestContext().put(VAU_NP, np);
        ValidToResponseType response = entitlementsApi.setEntitlementPs(insurantId, USER_AGENT, entitlementRequest);
        return Instant.ofEpochMilli(response.getValidTo().getTime());
    }
}
