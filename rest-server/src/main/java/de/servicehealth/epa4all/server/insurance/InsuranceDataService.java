package de.servicehealth.epa4all.server.insurance;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.logging.LogField;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static de.servicehealth.epa4all.server.filetracker.IFolderService.LOCAL_FOLDER;
import static de.servicehealth.logging.LogContext.withMdc;
import static de.servicehealth.logging.LogField.EGK_HANDLE;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.TELEMATIK_ID;

@ApplicationScoped
public class InsuranceDataService {

    private static final Logger log = LoggerFactory.getLogger(InsuranceDataService.class.getName());

    private final IKonnektorClient konnektorClient;
    private final FolderService folderService;
    private final VsdService vsdService;

    @Inject
    public InsuranceDataService(
        IKonnektorClient konnektorClient,
        FolderService folderService,
        VsdService vsdService
    ) {
        this.konnektorClient = konnektorClient;
        this.folderService = folderService;
        this.vsdService = vsdService;
    }

    public InsuranceData getData(
        String telematikId,
        String egkHandle,
        UserRuntimeConfig runtimeConfig
    ) throws CetpFault {
        String kvnr = konnektorClient.getKvnr(runtimeConfig, egkHandle);
        return getData(telematikId, kvnr);
    }

    public InsuranceData getData(String telematikId, String kvnr) {
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        return new VsdResponseFile(localFolder).load(telematikId, kvnr);
    }

    public InsuranceData initData(
        String telematikId,
        String egkHandle,
        String kvnr,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig,
        boolean propagateException
    ) {
        Map<LogField, String> mdcMap = Map.of(
            TELEMATIK_ID, telematikId,
            EGK_HANDLE, egkHandle == null ? "''" : egkHandle,
            INSURANT, kvnr == null ? "''" : kvnr,
            SMCB_HANDLE, smcbHandle == null ? "''": smcbHandle
        );
        return withMdc(mdcMap, () -> {
            try {
                String eHandle = egkHandle;
                if (eHandle == null) {
                    eHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
                }
                String insurantId = vsdService.readVsd(eHandle, smcbHandle, runtimeConfig, telematikId);
                InsuranceData localInsuranceData = getData(telematikId, insurantId);
                if (localInsuranceData == null) {
                    String msg = String.format("Unable to read VSD: KVNR=%s, EGK=%s, SMCB=%s", insurantId, egkHandle, smcbHandle);
                    throw new CetpFault(msg);
                }
                return localInsuranceData;
            } catch (Exception e) {
                log.error("Error while init InsuranceData", e);
                if (propagateException) {
                    throw new RuntimeException(e);
                } else {
                    return null;
                }
            }
        });
    }

    public void cleanUpInsuranceData(String telematikId, String kvnr) {
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        new VsdResponseFile(localFolder).cleanUp();
    }

    public Instant getEntitlementExpiry(String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        return new EntitlementFile(localFolder, kvnr).getEntitlement();
    }

    public void updateEntitlement(Instant validTo, String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        new EntitlementFile(localFolder, kvnr).updateEntitlement(validTo);
    }
}
