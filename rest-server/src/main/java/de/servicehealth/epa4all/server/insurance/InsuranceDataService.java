package de.servicehealth.epa4all.server.insurance;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import static de.servicehealth.epa4all.server.filetracker.IFolderService.LOCAL_FOLDER;

@ApplicationScoped
public class InsuranceDataService {

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
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        if (egkHandle == null) {
            egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
        }
        String insurantId = vsdService.readVsd(egkHandle, smcbHandle, runtimeConfig, telematikId);
        InsuranceData localInsuranceData = getData(telematikId, insurantId);
        if (localInsuranceData == null) {
            String msg = String.format("Unable to read VSD: KVNR=%s, EGK=%s, SMCB=%s", insurantId, egkHandle, smcbHandle);
            throw new CetpFault(msg);
        }
        return localInsuranceData;
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
