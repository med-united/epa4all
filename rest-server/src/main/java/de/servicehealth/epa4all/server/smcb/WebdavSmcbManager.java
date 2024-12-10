package de.servicehealth.epa4all.server.smcb;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.ReadVSDResponseEx;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Startup
public class WebdavSmcbManager {

    private static final Logger log = Logger.getLogger(WebdavSmcbManager.class.getName());

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
            File localMedFolder = folderService.getInsurantMedFolder(telematikId, insurantId, "local");
            new VSDResponseFile(localMedFolder).store(readVSDResponseEx.getReadVSDResponse());
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
}
