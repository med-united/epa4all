package de.servicehealth.epa4all.server.entitlement;

import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.logging.Logger;

@ApplicationScoped
public class EntitlementService {

    private static final Logger log = Logger.getLogger(EntitlementService.class.getName());

    IdpClient idpClient;
    InsuranceDataService insuranceDataService;

    @Inject
    public EntitlementService(IdpClient idpClient, InsuranceDataService insuranceDataService) {
        this.idpClient = idpClient;
        this.insuranceDataService = insuranceDataService;
    }

    public void setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String telematikId,
        String vauNp,
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
        ValidToResponseType response = entitlementsApi.setEntitlementPs(
            insurantId, userAgent, backend, vauNp, entitlementRequest
        );
        if (response.getValidTo() != null) {
            insuranceDataService.updateEntitlement(response.getValidTo().toInstant(), telematikId, insurantId);
        }
    }
}
