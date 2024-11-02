package de.servicehealth.epa4all.server.smcb;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
@Startup
public class SmcbManager {

    private static final Logger log = Logger.getLogger(SmcbManager.class.getName());

    private final Map<String, Set<FolderInfo>> smcbDocsMap = new ConcurrentHashMap<>();

    private final WebdavConfig webdavConfig;
    private final File rootFolder;

    @Inject
    public SmcbManager(WebdavConfig webdavConfig) {
        this.webdavConfig = webdavConfig;
        rootFolder = new File(webdavConfig.getRootFolder());
    }

    public void checkOrCreateSmcbFolders(String telematikId) {
        if (!rootFolder.exists()) {
            throw new IllegalStateException("Root SMC-B folder is absent");
        }
        String path = rootFolder.getAbsolutePath() + File.separator + telematikId;
        try {
            Path smcb = Files.createDirectories(Paths.get(path));
            String smcbFolder = smcb.toFile().getAbsolutePath();
            Set<FolderInfo> folders = smcbDocsMap.computeIfAbsent(smcbFolder, f -> new HashSet<>());
            webdavConfig.getSmcbFolders().forEach(f -> {
                try {
                    String[] parts = f.split("_");
                    String folderPath = smcbFolder + File.separator + parts[0];
                    Path dir = Paths.get(folderPath);
                    if (!dir.toFile().exists()) {
                        try {
                            Path folder = Files.createDirectories(dir);
                            folders.add(new FolderInfo(folder.toFile().getAbsolutePath(), parts[1]));
                        } catch (Exception e) {
                            log.severe(String.format("Unable to create SMC-V folder [%s]", folderPath));
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage());
                }
            });
        } catch (Exception e) {
            log.severe("Error while initing SMC-B folders -> " + e.getMessage());
        }
    }
}
