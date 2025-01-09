package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.entitlement.AuditEvidenceException;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ResponseProcessingException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public abstract class AbstractResource {

    protected final Logger log = Logger.getLogger(getClass().getName());

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    EntitlementService entitlementService;

    @Inject
    VauNpProvider vauNpProvider;

    @Inject
    IdpConfig idpConfig;

    @Inject
    protected EpaMultiService epaMultiService;

    @Inject
    @FromHttpPath
    protected UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TelematikId
    protected String telematikId;

    @Inject
    @SMCBHandle
    String smcbHandle;

    protected EpaContext prepareEpaContext(String kvnr) throws Exception {
        String errMsg = String.format("[%s] Error while building of the EPA Context", kvnr);
        EpaContext epaContext;
        try {
            epaContext = buildEpaContext(kvnr);
        } catch (AuditEvidenceException e) {
            log.log(Level.SEVERE, errMsg, e);
            insuranceDataService.cleanUpInsuranceData(telematikId, kvnr);
            throw e;
        } catch (Exception e) {
            log.log(Level.SEVERE, errMsg, e instanceof ResponseProcessingException ? e.getCause() : e);
            insuranceDataService.cleanUpInsuranceData(telematikId, kvnr);
            epaContext = buildEpaContext(kvnr);
        }
        return epaContext;
    }

    protected EpaContext buildEpaContext(String kvnr) throws Exception {
        InsuranceData insuranceData = insuranceDataService.getLocalInsuranceData(telematikId, kvnr);
        if (insuranceData == null) {
            insuranceData = insuranceDataService.readVsd(telematikId, null, kvnr, smcbHandle, userRuntimeConfig);
        }
        String insurantId = insuranceData.getInsurantId();
        EpaAPI epaAPI = epaMultiService.getEpaAPI(insurantId);
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();
        String backend = epaAPI.getBackend();
        String konnektorUrl = userRuntimeConfig.getConnectorBaseURL();
        String vauNp = vauNpProvider.getVauNp(smcbHandle, konnektorUrl, backend);

        boolean entitlementValid = false;
        if (vauNp != null) {
            Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, insurantId);
            if (validTo == null || validTo.isBefore(Instant.now())) {
                entitlementValid = entitlementService.setEntitlement(
                    userRuntimeConfig,
                    insuranceData,
                    epaAPI,
                    telematikId,
                    vauNp,
                    userAgent,
                    smcbHandle
                );
            } else {
                entitlementValid = true;
                log.info(String.format("[%s/%s] Entitlement is valid until %s", telematikId, insurantId, validTo));
            }
        } else {
            log.warning(String.format("[%s] VAU session is not found, skipping setEntitlement", backend));
        }
        return new EpaContext(backend, entitlementValid, insuranceData, prepareXHeaders(insurantId, userAgent, backend, vauNp));
    }

    private Map<String, String> prepareXHeaders(String insurantId, String userAgent, String backend, String vauNp) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, userAgent);
        attributes.put(X_BACKEND, backend);
        attributes.put(CLIENT_ID, idpConfig.getClientId());
        if (vauNp != null) {
            attributes.put(VAU_NP, vauNp);
        }
        return attributes;
    }
}
