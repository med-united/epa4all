package de.servicehealth.epa4all.server.smcb;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@ApplicationScoped
@Startup
public class SmcbManager {

    private static final Logger log = Logger.getLogger(SmcbManager.class.getName());

    private final WebdavConfig webdavConfig;
    private final File rootFolder;

    @Inject
    public SmcbManager(WebdavConfig webdavConfig) {
        this.webdavConfig = webdavConfig;

        rootFolder = new File(webdavConfig.getRootFolder());
        // if (!rootFolder.exists()) {
        //     throw new IllegalStateException("Root SMC-B folder is absent");
        // }
    }

    public boolean checkOrCreateSmcbFolders(String telematikId) {
        String path = rootFolder.getAbsolutePath() + File.separator + telematikId;
        try {
            Path smcb = Files.createDirectories(Paths.get(path));
            final AtomicBoolean created = new AtomicBoolean(true);
            webdavConfig.getSmcbFolders().forEach(f -> {
                try {
                    String smcbFolder = smcb.toFile().getAbsolutePath();
                    Path dir = Paths.get(smcbFolder + File.separator + f);
                    created.set(created.get() && Files.createDirectories(dir).toFile().exists()); // TODO check attributes
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage());
                }
            });
            return created.get();
        } catch (Exception e) {
            log.severe("Error while initing SMC-B folders -> " + e.getMessage());
            return false;
        }
    }
}
