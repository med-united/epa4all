package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.jmx.TelematikMXBeanRegistry;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.utils.ServerUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static de.servicehealth.epa4all.server.filetracker.FileOp.Create;
import static de.servicehealth.epa4all.server.filetracker.FileOp.Delete;
import static java.io.File.separator;

@ApplicationScoped
public class FolderService implements IFolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderService.class.getName());

    private final File rootFolder;
    private final WebdavConfig webdavConfig;
    private final FileEventSender fileEventSender;
    private final TelematikMXBeanRegistry telematikMXBeanRegistry;

    @Inject
    public FolderService(
        WebdavConfig webdavConfig,
        FileEventSender fileEventSender,
        TelematikMXBeanRegistry telematikMXBeanRegistry
    ) {
        this.webdavConfig = webdavConfig;
        this.fileEventSender = fileEventSender;
        this.telematikMXBeanRegistry = telematikMXBeanRegistry;

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
        var exists = new File(rootFolder, telematikId).exists();
        String path = String.join(separator, rootFolder.getAbsolutePath(), telematikId);
        return () -> {
            File folder = getOrCreateFolder(path);
            if (!exists) {
                telematikMXBeanRegistry.registerTelematikId(telematikId);
            }
            return folder;
        };
    }

    public File initInsurantFolders(String telematikId, String insurantId) {
        File telematikFolder = getTelematikFolder(telematikId);
        if (insurantId != null && !insurantId.trim().isEmpty()) {
            String telematikFolderPath = telematikFolder.getAbsolutePath();
            webdavConfig.getSmcbFolders().keySet().forEach(folder -> {
                    try {
                        getOrCreateFolder(String.join(separator, telematikFolderPath, insurantId, folder));
                    } catch (Exception e) {
                        log.error(String.format("Error while creating folder '%s'", folder), e);
                    }
                }
            );
        }
        return telematikFolder;
    }

    public void deleteFile(String telematikId, String insurantId, String fileName) throws Exception {
        File insurantFolder = getInsurantFolder(telematikId, insurantId);
        ChecksumFile checksumFile = new ChecksumFile(insurantFolder);
        File file = new File(fileName);
        if (file.exists()) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileUtils.forceDelete(file);
            checksumFile.removeChecksum(bytes, insurantId);

            log.info("File %s is deleted".formatted(file.getAbsolutePath()));
            fileEventSender.sendAsync(new FileEvent(Delete, telematikId, List.of(file)));
        }
    }

    public void storeNewFile(
        String fileName,
        String folderCode,
        String telematikId,
        String insurantId,
        byte[] documentBytes
    ) throws Exception {
        ChecksumFile checksumFile = new ChecksumFile(getInsurantFolder(telematikId, insurantId));
        if (checksumFile.appendChecksum(documentBytes, insurantId)) {
            File medFolder = getMedFolder(telematikId, insurantId, folderCode);
            File file = new File(medFolder, fileName.replace(":", "_"));
            if (!file.exists()) {
                writeBytesToFile(telematikId, documentBytes, file);
            }
        }
    }

    public void writeBytesToFile(String telematikId, byte[] bytes, File outFile) {
        try {
            ServerUtils.writeBytesToFile(bytes, outFile);
            if (telematikId != null) {
                fileEventSender.sendAsync(new FileEvent(Create, telematikId, List.of(outFile)));
            }
        } catch (IOException e) {
            log.error("Error while saving file: " + outFile.getAbsolutePath(), e);
        }
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