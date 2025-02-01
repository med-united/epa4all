package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.health.service.cetp.utils.Utils.saveDataToFile;
import static java.io.File.separator;

@ApplicationScoped
public class FolderService implements IFolderService {

    private static final Logger log = Logger.getLogger(FolderService.class.getName());

    private final File rootFolder;
    private final WebdavConfig webdavConfig;

    @Inject
    public FolderService(WebdavConfig webdavConfig) {
        this.webdavConfig = webdavConfig;

        rootFolder = new File(webdavConfig.getRootFolder());
        if (!rootFolder.exists()) {
            log.info("Creating webdav directory: " + rootFolder.getAbsolutePath());
            rootFolder.mkdirs();
        }
        if (!rootFolder.exists()) {
            throw new IllegalStateException("Webdav directory does not exist or is not a directory: " + rootFolder.getAbsolutePath());
        }
    }

    @Override
    public File getRootFolder() {
        return rootFolder;
    }

    @Override
    public Supplier<File> getTelematikFolderSupplier(String telematikId) {
        String path = String.join(separator, rootFolder.getAbsolutePath(), telematikId);
        return () -> getOrCreateFolder(path);
    }

    public void initInsurantFolders(String telematikId, String insurantId) {
        String telematikFolderPath = getTelematikFolder(telematikId).getAbsolutePath();
        webdavConfig.getSmcbFolders().forEach(folderProperty -> {
                String[] parts = folderProperty.split("_");
                try {
                    getOrCreateFolder(String.join(separator, telematikFolderPath, insurantId, parts[0]));
                } catch (Exception e) {
                    log.log(Level.SEVERE, String.format("Error while creating folder '%s'", folderProperty), e);
                }
            }
        );
    }

    public void storeNewFile(
        String fileName,
        String folderCode,
        String telematikId,
        String insurantId,
        byte[] documentBytes
    ) throws Exception {
        if (appendChecksumFor(telematikId, insurantId, documentBytes)) {
            File medFolder = getMedFolder(telematikId, insurantId, folderCode);
            File file = new File(medFolder, fileName);
            if (!file.exists()) {
                saveDataToFile(documentBytes, file);
            }
        }
    }

    public boolean appendChecksumFor(String telematikId, String insurantId, byte[] documentBytes) throws Exception {
        ChecksumFile checksumFile = new ChecksumFile(getInsurantFolder(telematikId, insurantId));
        return checksumFile.appendChecksumFor(documentBytes, insurantId);
    }

    public Set<String> getChecksums(String telematikId, String insurantId) {
        try {
            return new ChecksumFile(getInsurantFolder(telematikId, insurantId)).getChecksums();
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while getting unique checksums for %s", insurantId), e);
        }
        return Set.of();
    }
}
