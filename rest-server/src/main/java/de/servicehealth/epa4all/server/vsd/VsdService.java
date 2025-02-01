package de.servicehealth.epa4all.server.vsd;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.xml.datatype.DatatypeFactory;
import java.io.File;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.filetracker.IFolderService.LOCAL_FOLDER;
import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.extractInsurantId;
import static de.servicehealth.utils.ServerUtils.compress;

@ApplicationScoped
public class VsdService {

    private static final Logger log = Logger.getLogger(VsdService.class.getName());

    private final MultiKonnektorService multiKonnektorService;
    private final FolderService folderService;

    @Inject
    public VsdService(
        MultiKonnektorService multiKonnektorService,
        FolderService folderService
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.folderService = folderService;
    }

    public static ReadVSDResponse buildSyntheticVSDResponse(String xml, byte[] bytes) throws Exception {
        ReadVSDResponse readVSDResponse = new ReadVSDResponse();
        readVSDResponse.setAllgemeineVersicherungsdaten(new byte[0]);
        readVSDResponse.setGeschuetzteVersichertendaten(new byte[0]);
        readVSDResponse.setPersoenlicheVersichertendaten(new byte[0]);

        byte[] gzipBytes = xml != null ? compress(xml.getBytes()) : compress(bytes);
        readVSDResponse.setPruefungsnachweis(gzipBytes);

        VSDStatusType vsdStatus = new VSDStatusType();
        vsdStatus.setStatus("0");
        vsdStatus.setVersion("5.2.0");
        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        vsdStatus.setTimestamp(datatypeFactory.newXMLGregorianCalendar(gregorianCalendar));
        readVSDResponse.setVSDStatus(vsdStatus);

        return readVSDResponse;
    }

    public synchronized String readVsd(
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig,
        String telematikId
    ) throws Exception {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        ContextType context = servicePorts.getContextType();
        if (context.getUserId() == null || context.getUserId().isEmpty()) {
            context.setUserId(UUID.randomUUID().toString());
        }
        ReadVSD readVSD = prepareReadVSDRequest(context, egkHandle, smcbHandle);
        ReadVSDResponse readVSDResponse = servicePorts.getVSDServicePortType().readVSD(readVSD);
        String insurantId = extractInsurantId(readVSDResponse, false);
        saveVsdFile(telematikId, insurantId, readVSDResponse);
        return insurantId;
    }

    public void saveVsdFile(String telematikId, String insurantId, ReadVSDResponse readVSDResponse) {
        try {
            // 1. Make sure all med folders are created
            folderService.initInsurantFolders(telematikId, insurantId);

            // 2. Store VDS response into "local" folder
            File localFolder = folderService.getMedFolder(telematikId, insurantId, LOCAL_FOLDER);
            new VsdResponseFile(localFolder).store(readVSDResponse);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
        }
    }

    private ReadVSD prepareReadVSDRequest(
        ContextType context,
        String egkHandle,
        String smcbHandle
    ) {
        ReadVSD readVSD = new ReadVSD();
        readVSD.setContext(context);
        readVSD.setEhcHandle(egkHandle);
        readVSD.setHpcHandle(smcbHandle);
        readVSD.setReadOnlineReceipt(true);
        readVSD.setPerformOnlineCheck(true);
        return readVSD;
    }
}
