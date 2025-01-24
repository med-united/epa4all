package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@ApplicationScoped
public class FolderService {

    private static final Logger log = Logger.getLogger(FolderService.class.getName());

    private final File rootFolder;

    @Inject
    public FolderService(WebdavConfig webdavConfig) {
        rootFolder = new File(webdavConfig.getRootFolder());
        if (!rootFolder.exists()) {
        	log.info("Creating webdav directory: "+rootFolder.getAbsolutePath());
        	rootFolder.mkdirs();
        }
        if (!rootFolder.exists() || !rootFolder.isDirectory()) {
            throw new IllegalStateException("Webdav directory is does not exist or is not a directory. "+rootFolder.getAbsolutePath());
        }
    }

    public File[] getNestedFiles(File parent) {
        if (parent == null || !parent.exists() || !parent.isDirectory()) {
            return new File[0];
        }
        return parent.listFiles(File::isFile);
    }

    public File[] getNestedFolders(File parent) {
        if (parent == null || !parent.exists() || !parent.isDirectory()) {
            return new File[0];
        }
        return parent.listFiles(File::isDirectory);
    }

    public File[] getTelematikFolders() {
        return getNestedFolders(rootFolder);
    }

    public File getTelematikFolder(String telematikId) {
        return Arrays.stream(getTelematikFolders())
            .filter(f -> f.getName().equals(telematikId))
            .findFirst()
            .orElse(null);
    }

    public File[] getInsurantsFolders(String telematikId) {
        return getNestedFolders(getTelematikFolder(telematikId));
    }

    public File getInsurantFolder(String telematikId, String insurantId) {
        return Arrays.stream(getInsurantsFolders(telematikId))
            .filter(f -> f.getName().equals(insurantId))
            .findFirst()
            .orElse(null);
    }

    public File[] getInsurantsMedFolders(String telematikId, String insurantId) {
        return getNestedFolders(getInsurantFolder(telematikId, insurantId));
    }

    public File getInsurantMedFolder(String telematikId, String insurantId, String folderName) {
        return Arrays.stream(getInsurantsMedFolders(telematikId, insurantId))
            .filter(f -> f.getName().equals(folderName))
            .findFirst()
            .orElse(null);
    }

    public File[] getInsurantMedFolderFiles(String telematikId, String insurantId, String folderName) {
        return getNestedFiles(getInsurantMedFolder(telematikId, insurantId, folderName));
    }

    public List<File> getInsurantMedFoldersFiles(String telematikId, String insurantId, Set<String> extSet) {
        return Arrays.stream(getInsurantsMedFolders(telematikId, insurantId))
            .filter(f -> !f.getName().equals("local"))
            .flatMap(f -> {
                File[] nestedFiles = getNestedFiles(f);
                return nestedFiles == null ? Stream.empty() : Stream.of(nestedFiles);
            })
            .filter(f -> extSet == null || extSet.isEmpty() || extSet.stream().anyMatch(f.getName().toLowerCase()::endsWith))
            .toList();
    }

    public File createFolder(String path, File fallbackFolder) {
        File folder = new File(path);
        if (folder.exists()) {
            return folder;
        } else {
            boolean created = folder.mkdirs();
            if (created) {
                return folder;
            } else {
                String msg = String.format(
                    "Unable to create medical folder [%s], using fallback [%s]",
                    folder.getAbsolutePath(),
                    fallbackFolder.getAbsolutePath()
                );
                log.log(Level.SEVERE, msg);
                return fallbackFolder;
            }
        }
    }

    public void applyTelematikPath(String telematikId) {
        String pathname = rootFolder.getAbsolutePath() + File.separator + telematikId;
        File telematikFolder = new File(pathname);
        if (!telematikFolder.exists()) {
            boolean created = telematikFolder.mkdir();
            if (!created) {
                String msg = String.format("Telematik directory [%s] was not created", pathname);
                throw new IllegalStateException(msg);
            }
        }
    }

    public boolean appendChecksumFor(String telematikId, String insurantId, byte[] documentBytes) throws Exception {
        File insurantFolder = getInsurantFolder(telematikId, insurantId);
        ChecksumFile checksumFile = new ChecksumFile(insurantFolder, insurantId);
        return checksumFile.appendChecksumFor(documentBytes);
    }

    public Set<String> getChecksums(String telematikId, String insurantId) {
        Set<String> checksums = new HashSet<>();
        try {
            File insurantFolder = getInsurantFolder(telematikId, insurantId);
            ChecksumFile checksumFile = new ChecksumFile(insurantFolder, insurantId);
            checksums = checksumFile.getChecksums();
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while getting unique checksums for %s", insurantId), e);
        }
        return checksums;
    }
}
