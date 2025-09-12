package de.servicehealth.epa4all.server.insurance;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;

import static de.servicehealth.folder.IFolderService.LOCAL_FOLDER;

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

    public InsuranceData loadInsuranceDataEx(
        UserRuntimeConfig runtimeConfig,
        String egkHandle,
        String smcbHandle,
        String telematikId
    ) throws Exception {
        String kvnr = getKvnr(egkHandle, runtimeConfig);
        String insurantId = vsdService.read(egkHandle, smcbHandle, runtimeConfig, telematikId, kvnr);
        return getData(telematikId, insurantId);
    }

    public InsuranceData loadInsuranceData(
        UserRuntimeConfig runtimeConfig,
        String smcbHandle,
        String telematikId,
        String kvnr
    ) {
        try {
            String egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
            String insurantId = vsdService.read(egkHandle, smcbHandle, runtimeConfig, telematikId, kvnr);
            return getData(telematikId, insurantId);
        } catch (Exception e) {
            log.warn("Error while get InsuranceData: %s".formatted(e.getMessage()));
            return null;
        }
    }

    public String getKvnr(String egkHandle, UserRuntimeConfig runtimeConfig) {
        try {
            return konnektorClient.getKvnr(runtimeConfig, egkHandle);
        } catch (Exception e) {
            log.error("Error while konnektorClient.getKvnr", e);
            return null;
        }
    }

    public InsuranceData getData(String telematikId, String egkHandle, UserRuntimeConfig runtimeConfig) {
        String kvnr = getKvnr(egkHandle, runtimeConfig);
        return kvnr == null ? null : getData(telematikId, kvnr);
    }

    public InsuranceData getData(String telematikId, String kvnr) {
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        return localFolder == null ? null : new VsdResponseFile(localFolder).load(telematikId, kvnr);
    }

    public void cleanUpInsuranceData(String telematikId, String kvnr) {
        log.info("Deleting local insurance data, kvnr={}", kvnr);
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        if (localFolder != null) {
            new VsdResponseFile(localFolder).cleanUp();
        }
    }

    public Instant getEntitlementExpiry(String telematikId, String kvnr) {
        try {
            File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
            return localFolder == null ? null : new EntitlementFile(localFolder, kvnr).getEntitlement();
        } catch (Exception e) {
            log.error("Error while getEntitlementExpiry", e);
            return null;
        }
    }

    public void setEntitlementExpiry(Instant validTo, String telematikId, String kvnr) {
        try {
            File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
            if (localFolder != null) {
                new EntitlementFile(localFolder, kvnr).setEntitlement(validTo);
            }
        } catch (Exception e) {
            log.error("Error while updateEntitlement", e);
        }
    }
}
