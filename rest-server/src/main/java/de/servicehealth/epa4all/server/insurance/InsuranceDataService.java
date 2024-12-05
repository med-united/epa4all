package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.EntitlementFile;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.server.vsd.VSDService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createDocument;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCEntity;

@ApplicationScoped
public class InsuranceDataService {

    private static final Logger log = Logger.getLogger(InsuranceDataService.class.getName());

    private final WebdavSmcbManager webdavSmcbManager;
    private final IKonnektorClient konnektorClient;
    private final FolderService folderService;
    private final VSDService vsdService;
    private final Event<ReadVSDResponseEx> readVSDResponseExEvent;

    @Inject
    public InsuranceDataService(
        WebdavSmcbManager webdavSmcbManager,
        IKonnektorClient konnektorClient,
        FolderService folderService,
        VSDService vsdService,
        Event<ReadVSDResponseEx> readVSDResponseExEvent
    ) {
        this.webdavSmcbManager = webdavSmcbManager;
        this.konnektorClient = konnektorClient;
        this.folderService = folderService;
        this.vsdService = vsdService;
        this.readVSDResponseExEvent = readVSDResponseExEvent;
    }

    public InsuranceData getInsuranceDataOrReadVSD(
        String telematikId,
        String egkHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        String kvnr = konnektorClient.getKvnr(runtimeConfig, egkHandle);
        String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
        return getInsuranceDataOrReadVSD(telematikId, kvnr, smcbHandle, runtimeConfig);
    }

    public InsuranceData getInsuranceDataOrReadVSD(
        String telematikId,
        String kvnr,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        InsuranceData localInsuranceData = getLocalInsuranceData(telematikId, kvnr);
        if (localInsuranceData != null) {
            return localInsuranceData;
        }
        folderService.applyTelematikPath(telematikId);

        String egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
        ReadVSDResponse readVSDResponse = vsdService.readVSD(egkHandle, smcbHandle, runtimeConfig);
        String xInsurantId = extractInsurantId(readVSDResponse);
        if (kvnr == null || kvnr.equals(xInsurantId)) {
            // ReadVSDResponseEx must be sent synchronously to get valid local InsuranceData.
            readVSDResponseExEvent.fire(new ReadVSDResponseEx(telematikId, xInsurantId, readVSDResponse));
            return getLocalInsuranceData(telematikId, xInsurantId);
        }
        throw new IllegalStateException(String.format("Mismatch found: KVNR=%s, xInsurantId=%s", kvnr, xInsurantId));
    }

    public InsuranceData getLocalInsuranceData(String telematikId, String kvnr) {
        if (kvnr == null) {
            return null;
        }
        File localVSDFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        return webdavSmcbManager.getFileSystemInsuranceData(localVSDFolder, kvnr);
    }

    public Instant getEntitlementExpiry(String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        return new EntitlementFile(localFolder, kvnr).getEntitlement();
    }

    public void updateEntitlement(Instant validTo, String telematikId, String kvnr) throws IOException {
        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        new EntitlementFile(localFolder, kvnr).updateEntitlement(validTo);
    }

    private String extractInsurantId(ReadVSDResponse readVSDResponse) throws Exception {
        Document doc = createDocument(readVSDResponse.getPruefungsnachweis(), true);
        String e = doc.getElementsByTagName("E").item(0).getTextContent();

        if (e.equals("3")) {
            byte[] bytes = readVSDResponse.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML patient = createUCEntity(bytes, true);
            String versichertenId = patient.getVersicherter().getVersichertenID();
            log.fine("VSDM result: " + e + " VersichertenID: " + versichertenId);
            return versichertenId;
        } else {
            String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
            byte[] base64Binary = DatatypeConverter.parseBase64Binary(pz);
            String base64PN = new String(base64Binary);
            return base64PN.substring(0, 10);
        }
    }
}
