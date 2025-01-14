package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.smcb.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.smcb.VsdResponseFile.extractInsurantId;

@ApplicationScoped
public class InsuranceDataService {

    private static final Logger log = Logger.getLogger(InsuranceDataService.class.getName());

    private final IKonnektorClient konnektorClient;
    private final FolderService folderService;
    private final VsdService vsdService;
    private final Event<ReadVSDResponseEx> readVSDResponseExEvent;

    @Inject
    public InsuranceDataService(
        IKonnektorClient konnektorClient,
        FolderService folderService,
        VsdService vsdService,
        Event<ReadVSDResponseEx> readVSDResponseExEvent
    ) {
        this.konnektorClient = konnektorClient;
        this.folderService = folderService;
        this.vsdService = vsdService;
        this.readVSDResponseExEvent = readVSDResponseExEvent;
    }

    public InsuranceData getLocalInsuranceData(
        String telematikId,
        String egkHandle,
        UserRuntimeConfig runtimeConfig
    ) throws CetpFault {
        String kvnr = konnektorClient.getKvnr(runtimeConfig, egkHandle);
        return getLocalInsuranceData(telematikId, kvnr);
    }

    public InsuranceData getLocalInsuranceData(String telematikId, String kvnr) {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        if (localFolder == null) {
            return null;
        }
        return new VsdResponseFile(localFolder).load(kvnr);
    }

    public InsuranceData readVsd(
        String telematikId,
        String egkHandle,
        String kvnr,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        folderService.applyTelematikPath(telematikId);

        if (egkHandle == null) {
            egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
        }
        
        ReadVSDResponse readVSDResponse = vsdService.readVsd(egkHandle, smcbHandle, runtimeConfig);
        String insurantId = extractInsurantId(readVSDResponse, false);

        // ReadVSDResponseEx must be sent synchronously to get valid local InsuranceData.
        readVSDResponseExEvent.fire(new ReadVSDResponseEx(telematikId, insurantId, readVSDResponse));
        InsuranceData localInsuranceData = getLocalInsuranceData(telematikId, insurantId);
        if (localInsuranceData == null) {
            String msg = String.format("Unable to read VSD: KVNR=%s, EGK=%s, SMCB=%s", insurantId, egkHandle, smcbHandle);
            throw new CetpFault(msg);
        }
        return localInsuranceData;
    }

    public void cleanUpInsuranceData(String telematikId, String kvnr) {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        if (localFolder != null) {
            new VsdResponseFile(localFolder).cleanUp();
        }
    }

    public Instant getEntitlementExpiry(String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        return new EntitlementFile(localFolder, kvnr).getEntitlement();
    }

    public void updateEntitlement(Instant validTo, String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        new EntitlementFile(localFolder, kvnr).updateEntitlement(validTo);
    }
}
