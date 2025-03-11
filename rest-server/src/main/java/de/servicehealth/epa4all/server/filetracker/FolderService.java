package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.folder.IFolderService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.utils.ServerUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.io.File.separator;

@ApplicationScoped
public class FolderService implements IFolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderService.class.getName());

    private final File rootFolder;
    private final WebdavConfig webdavConfig;
    private final FileEventSender fileEventSender;

    @Inject
    public FolderService(WebdavConfig webdavConfig, FileEventSender fileEventSender) {
        this.webdavConfig = webdavConfig;
        this.fileEventSender = fileEventSender;

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

    public File initInsurantFolders(String telematikId, String insurantId) {
        File telematikFolder = getTelematikFolder(telematikId);
        String telematikFolderPath = telematikFolder.getAbsolutePath();
        webdavConfig.getSmcbFolders().forEach(folderProperty -> {
                String[] parts = folderProperty.split("_");
                try {
                    getOrCreateFolder(String.join(separator, telematikFolderPath, insurantId, parts[0]));
                } catch (Exception e) {
                    log.error(String.format("Error while creating folder '%s'", folderProperty), e);
                }
            }
        );
        return telematikFolder;
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
                writeBytesToFile(telematikId, documentBytes, file);
            }
        }
    }

    public void writeBytesToFile(String telematikId, byte[] bytes, File outFile) {
        try {
            ServerUtils.writeBytesToFile(bytes, outFile);
            if (telematikId != null) {
                fileEventSender.sendAsync(new FileEvent(telematikId, List.of(outFile)));
            }
        } catch (IOException e) {
            log.error("Error while saving file: " + outFile.getAbsolutePath(), e);
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
            log.error(String.format("Error while getting unique checksums for %s", insurantId), e);
        }
        return Set.of();
    }
}