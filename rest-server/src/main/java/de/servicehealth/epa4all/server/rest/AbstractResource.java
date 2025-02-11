package de.servicehealth.epa4all.server.rest;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaConfig;
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
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.logging.LogField;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ResponseProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static de.servicehealth.logging.LogContext.withMdcEx;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.TELEMATIKID;
import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public abstract class AbstractResource {

    protected final Logger log = LoggerFactory.getLogger(getClass().getName());

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    EntitlementService entitlementService;

    @Inject
    VauNpProvider vauNpProvider;

    @Inject
    VsdService vsdService;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    IdpConfig idpConfig;

    @Inject
    EpaConfig epaConfig;

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
            log.error(errMsg, e);
            insuranceDataService.cleanUpInsuranceData(telematikId, kvnr);
            throw e;
        } catch (Exception e) {
            log.error(errMsg, e instanceof ResponseProcessingException ? e.getCause() : e);
            insuranceDataService.cleanUpInsuranceData(telematikId, kvnr);
            epaContext = buildEpaContext(kvnr);
        }
        return epaContext;
    }

    private InsuranceData getInsuranceData(String kvnr) {
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
        if (insuranceData == null) {
            try {
                String egkHandle = konnektorClient.getEgkHandle(userRuntimeConfig, kvnr);
                String insurantId = vsdService.readVsd(egkHandle, smcbHandle, userRuntimeConfig, telematikId);
                insuranceData = insuranceDataService.getData(telematikId, insurantId);
            } catch (Exception e) {
                log.error("Error while get InsuranceData", e);
                return null;
            }
        }
        return insuranceData;
    }

    protected EpaContext buildEpaContext(String kvnr) throws Exception {
        Map<LogField, String> mdcMap = Map.of(
            TELEMATIKID, telematikId,
            INSURANT, kvnr,
            SMCB_HANDLE, smcbHandle
        );
        return withMdcEx(mdcMap, () -> {
            InsuranceData insuranceData = getInsuranceData(kvnr);
            String insurantId = insuranceData == null ? kvnr : insuranceData.getInsurantId();
            EpaAPI epaAPI = epaMultiService.getEpaAPI(insurantId);
            String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();
            String konnektorHost = userRuntimeConfig.getKonnektorHost();
            String workplaceId = userRuntimeConfig.getWorkplaceId();
            String backend = epaAPI.getBackend();
            Optional<String> vauNpOpt = vauNpProvider.getVauNp(smcbHandle, konnektorHost, workplaceId, backend);

            boolean entitlementIsSet = true;
            if (epaConfig.isEpaEntitlementMandatory()) {
                Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, insurantId);
                entitlementIsSet = validTo != null && validTo.isAfter(Instant.now());
                if (vauNpOpt.isPresent() && !entitlementIsSet) {
                    try {
                        entitlementIsSet = entitlementService.setEntitlement(
                            userRuntimeConfig,
                            insuranceData,
                            epaAPI,
                            telematikId,
                            vauNpOpt.get(),
                            userAgent,
                            smcbHandle
                        );
                    } catch (Exception ignored) {
                    }
                }
            }
            Map<String, String> xHeaders = prepareXHeaders(insurantId, userAgent, backend, vauNpOpt);
            return new EpaContext(insurantId, backend, entitlementIsSet, insuranceData, xHeaders);
        });
    }

    private Map<String, String> prepareXHeaders(
        String insurantId,
        String userAgent,
        String backend,
        Optional<String> vauNpOpt
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, userAgent);
        attributes.put(X_BACKEND, backend);
        attributes.put(CLIENT_ID, idpConfig.getClientId());
        vauNpOpt.ifPresent(vauNp -> attributes.put(VAU_NP, vauNp));
        return attributes;
    }
}
