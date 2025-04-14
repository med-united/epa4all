package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.ResponseAction;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.epa4all.server.idp.vaunp.VauSessionsJob;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.logging.LogField;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static de.servicehealth.logging.LogContext.resultMdcEx;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.TELEMATIKID;
import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;

public abstract class AbstractResource {

    protected final Logger log = LoggerFactory.getLogger(getClass().getName());

    private static final ConcurrentHashMap<Integer, Semaphore> deduplicationMap = new ConcurrentHashMap<>();

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    EntitlementService entitlementService;

    @Inject
    VauSessionsJob vauSessionsJob;

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

    protected Response deduplicatedCall(
        String path,
        String query,
        Integer hash,
        ResponseAction action
    ) throws Exception {
        if (deduplicationMap.computeIfAbsent(hash, key -> new Semaphore(1)).tryAcquire()) {
            try {
                return action.execute();
            } finally {
                Semaphore semaphore = deduplicationMap.remove(hash);
                if (semaphore != null) {
                    semaphore.release();
                }
            }
        } else {
            log.warn(String.format("[DUPLICATED Request] Path=%s Query=%s", path, query));
            return Response.status(Response.Status.TOO_MANY_REQUESTS).build();
        }
    }

    protected EpaContext prepareEpaContext(String kvnr) throws Exception {
        String errMsg = String.format("[%s] Error while building of the EPA Context", kvnr);
        EpaContext epaContext;
        try {
            epaContext = buildEpaContext(kvnr);
        } catch (Exception e) {
            log.error(errMsg, e instanceof ResponseProcessingException ? e.getCause() : e);
            insuranceDataService.cleanUpInsuranceData(telematikId, kvnr);
            epaContext = buildEpaContext(kvnr);
        }
        return epaContext;
    }

    protected EpaContext buildEpaContext(String kvnr) throws Exception {
        Map<LogField, String> mdcMap = Map.of(
            TELEMATIKID, telematikId,
            INSURANT, kvnr,
            SMCB_HANDLE, smcbHandle
        );
        return resultMdcEx(mdcMap, () -> {
            InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);

            // todo - confirm if PNW check is needed
            if (insuranceData == null || insuranceData.getPersoenlicheVersichertendaten() == null) {
                insuranceData = insuranceDataService.loadInsuranceData(userRuntimeConfig, smcbHandle, telematikId, kvnr);
            }
            String insurantId = insuranceData == null ? kvnr : insuranceData.getInsurantId();
            EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
            String userAgent = epaConfig.getEpaUserAgent();
            String backend = epaApi.getBackend();
            Instant entitlementExpiry = entitlementService.getEntitlementExpiry(
                userRuntimeConfig, insuranceData, epaApi, userAgent, smcbHandle, telematikId, insurantId
            );
            Map<String, String> xHeaders = prepareXHeaders(userAgent, backend, Optional.of(insurantId));
            return new EpaContext(insurantId, backend, entitlementExpiry, insuranceData, xHeaders);
        });
    }

    protected Map<String, String> prepareXHeaders(
        String userAgent,
        String backend,
        Optional<String> insurantIdOpt
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(X_KONNEKTOR, userRuntimeConfig.getKonnektorHost());
        attributes.put(X_WORKPLACE, userRuntimeConfig.getWorkplaceId());
        attributes.put(X_USER_AGENT, userAgent);
        attributes.put(X_BACKEND, backend);
        attributes.put(CLIENT_ID, idpConfig.getClientId());
        insurantIdOpt.ifPresent(insurantId -> attributes.put(X_INSURANT_ID, insurantId));
        return attributes;
    }
}
