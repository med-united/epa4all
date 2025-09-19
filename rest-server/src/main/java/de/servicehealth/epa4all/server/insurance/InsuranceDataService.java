package de.servicehealth.epa4all.server.insurance;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.jmx.TelematikMXBeanRegistry;
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

    private final TelematikMXBeanRegistry telematikMXBeanRegistry;
    private final IKonnektorClient konnektorClient;
    private final FolderService folderService;
    private final VsdService vsdService;

    @Inject
    public InsuranceDataService(
        TelematikMXBeanRegistry telematikMXBeanRegistry,
        IKonnektorClient konnektorClient,
        FolderService folderService,
        VsdService vsdService
    ) {
        this.telematikMXBeanRegistry = telematikMXBeanRegistry;
        this.konnektorClient = konnektorClient;
        this.folderService = folderService;
        this.vsdService = vsdService;
    }

    public InsuranceData loadInsuranceData(
        UserRuntimeConfig runtimeConfig,
        String egkHandle,
        String smcbHandle,
        String telematikId,
        String fallbackKvnr
    ) throws Exception {
        String insurantId = vsdService.read(egkHandle, smcbHandle, runtimeConfig, telematikId, fallbackKvnr);
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

    private boolean newPatient(String telematikId, String kvnr) {
        File telematikFolder = folderService.getTelematikFolder(telematikId);
        return !new File(telematikFolder, kvnr).exists();
    }

    public InsuranceData getData(String telematikId, String kvnr) {
        boolean newPatient = newPatient(telematikId, kvnr);
        try {
            File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
            return new VsdResponseFile(localFolder).load(telematikId, kvnr);
        } finally {
            if (newPatient) {
                telematikMXBeanRegistry.registerNewPatient(telematikId);
            }
        }
    }

    public void cleanUpInsuranceData(String telematikId, String kvnr) {
        log.info("Deleting local insurance data, kvnr={}", kvnr);
        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        new VsdResponseFile(localFolder).cleanUp();
    }

    public Instant getEntitlementExpiry(String telematikId, String kvnr) {
        try {
            File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
            return new EntitlementFile(localFolder, kvnr).getEntitlement();
        } catch (Exception e) {
            log.error("Error while getEntitlementExpiry", e);
            return null;
        }
    }

    public void setEntitlementExpiry(Instant validTo, String telematikId, String kvnr) {
        try {
            File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
            new EntitlementFile(localFolder, kvnr).setEntitlement(validTo);
        } catch (Exception e) {
            log.error("Error while updateEntitlement", e);
        }
    }
}