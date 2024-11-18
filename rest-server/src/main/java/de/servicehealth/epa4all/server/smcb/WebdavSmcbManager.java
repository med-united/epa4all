package de.servicehealth.epa4all.server.smcb;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import de.servicehealth.epa4all.server.insurance.ReadVSDResponseEx;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.health.service.cetp.utils.Utils.unzipAndSaveDataToFile;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCEntity;

@ApplicationScoped
@Startup
public class WebdavSmcbManager {

    private static final Logger log = Logger.getLogger(WebdavSmcbManager.class.getName());

    public static final String ALLGEMEINE_VERSICHERUNGSDATEN_XML = "AllgemeineVersicherungsdaten.xml";
    public static final String PERSOENLICHE_VERSICHERTENDATEN_XML = "PersoenlicheVersichertendaten.xml";
    public static final String GESCHUETZTE_VERSICHERTENDATEN_XML = "GeschuetzteVersichertendaten.xml";
    public static final String PRUEFUNGSNACHWEIS_XML = "Pruefungsnachweis.xml";

    static JAXBContext readVSDJaxbContext;

    static {
        try {
            readVSDJaxbContext = JAXBContext.newInstance(ReadVSDResponse.class);
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Could not create JAXBContext", e);
        }
    }

    private final FolderService folderService;
    private final WebdavConfig webdavConfig;

    @Inject
    public WebdavSmcbManager(
        FolderService folderService,
        WebdavConfig webdavConfig
    ) {
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
    }

    public void onRead(@Observes ReadVSDResponseEx readVSDResponseEx) {
        try {
            String telematikId = readVSDResponseEx.getTelematikId();
            String insurantId = readVSDResponseEx.getInsurantId();

            // 1. Make sure all med folders are created
            String telematikFolderPath = folderService.getTelematikFolder(telematikId).getAbsolutePath();
            webdavConfig.getSmcbFolders().forEach(folderProperty ->
                initFolder(telematikFolderPath, insurantId, folderProperty)
            );

            // 2. Store VDS response into "local" folder
            prepareLocalXMLs(telematikId, insurantId, readVSDResponseEx.getReadVSDResponse());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
        }
    }

    private void initFolder(
        String telematikFolderPath,
        String insurantId,
        String folderProperty
    ) {
        String[] parts = folderProperty.split("_");
        String folderName = parts[0];
        String uuid = parts[1]; // todo folder user attribute

        String path = telematikFolderPath + File.separator + insurantId + File.separator + folderName;
        folderService.createFolder(path, null);
    }

    public InsuranceData getFileSystemInsuranceData(File localInsurantFolder, String kvnr) {
        if (localInsurantFolder == null || !localInsurantFolder.isDirectory()) {
            return null;
        }
        try {
            File readVSDResponseFile = new File(localInsurantFolder, "ReadVSDResponse.xml");
            if (readVSDResponseFile.exists()) {
                byte[] allgemeineVersicherungsdaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(ALLGEMEINE_VERSICHERUNGSDATEN_XML));
                byte[] persoenlicheVersichertendaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(PERSOENLICHE_VERSICHERTENDATEN_XML));
                byte[] geschuetzteVersichertendaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(GESCHUETZTE_VERSICHERTENDATEN_XML));
                byte[] pruefungsnachweis = Files.readAllBytes(localInsurantFolder.toPath().resolve(PRUEFUNGSNACHWEIS_XML));

                Document doc = InsuranceXmlUtils.createDocument(pruefungsnachweis, false);
                String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();

                return new InsuranceData(
                    pz,
                    kvnr,
                    createUCEntity(persoenlicheVersichertendaten, false),
                    createUCEntity(geschuetzteVersichertendaten, false),
                    createUCEntity(allgemeineVersicherungsdaten, false)
                );
            } else {
                return null;
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while reading insurance data for KVNR=%s", kvnr));
        }
        return null;
    }

    private void prepareLocalXMLs(String telematikId, String insurantId, ReadVSDResponse readVSDResponse) throws Exception {
        File localMedFolder = folderService.getInsurantMedFolder(telematikId, insurantId, "local");
        File readVSDResponseFile = new File(localMedFolder, "ReadVSDResponse.xml");
        if (!readVSDResponseFile.exists()) {
            boolean created = readVSDResponseFile.createNewFile();
            if (!created) {
                String msg = String.format("ReadVSDResponse.xml was not created for [%s/%s]", telematikId, insurantId);
                throw new IllegalStateException(msg);
            }
        }
        readVSDJaxbContext.createMarshaller().marshal(readVSDResponse, new FileOutputStream(readVSDResponseFile));
        try {
            File allgemeineVersicherungsdatenFile = new File(localMedFolder, ALLGEMEINE_VERSICHERUNGSDATEN_XML);
            unzipAndSaveDataToFile(readVSDResponse.getAllgemeineVersicherungsdaten(), allgemeineVersicherungsdatenFile);
            File persoenlicheVersichertendatenFile = new File(localMedFolder, PERSOENLICHE_VERSICHERTENDATEN_XML);
            unzipAndSaveDataToFile(readVSDResponse.getPersoenlicheVersichertendaten(), persoenlicheVersichertendatenFile);
            File geschuetzteVersichertendatenFile = new File(localMedFolder, GESCHUETZTE_VERSICHERTENDATEN_XML);
            unzipAndSaveDataToFile(readVSDResponse.getGeschuetzteVersichertendaten(), geschuetzteVersichertendatenFile);
            File pruefungsnachweisFile = new File(localMedFolder, PRUEFUNGSNACHWEIS_XML);
            unzipAndSaveDataToFile(readVSDResponse.getPruefungsnachweis(), pruefungsnachweisFile);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
        }
    }
}
