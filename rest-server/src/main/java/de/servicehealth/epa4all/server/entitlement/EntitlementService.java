package de.servicehealth.epa4all.server.entitlement;

import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.UNDEFINED_PZ;

@ApplicationScoped
public class EntitlementService {

    private static final Logger log = Logger.getLogger(EntitlementService.class.getName());

    public static final String AUDIT_EVIDENCE_NO_DEFINED = "AuditEvidence is not defined";

    IdpClient idpClient;
    InsuranceDataService insuranceDataService;

    @Inject
    public EntitlementService(IdpClient idpClient, InsuranceDataService insuranceDataService) {
        this.idpClient = idpClient;
        this.insuranceDataService = insuranceDataService;
    }

    public boolean setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String telematikId,
        String vauNp,
        String userAgent,
        String smcbHandle
    ) throws Exception {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        if (UNDEFINED_PZ.equalsIgnoreCase(pz)) {
            String msg = String.format("%s for KVNR=%s, skipping the request", AUDIT_EVIDENCE_NO_DEFINED, insurantId);
            throw new AuditEvidenceException(msg);
        }
        String jwt = idpClient.createEntitlementPSJWT(smcbHandle, pz, userRuntimeConfig);

        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        entitlementRequest.setJwt(jwt);

        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        ValidToResponseType response = entitlementsApi.setEntitlementPs(
            insurantId, userAgent, epaAPI.getBackend(), vauNp, entitlementRequest
        );
        if (response.getValidTo() != null) {
            insuranceDataService.updateEntitlement(response.getValidTo().toInstant(), telematikId, insurantId);
            return true;
        } else {
            return false;
        }
    }
}
