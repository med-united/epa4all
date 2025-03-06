package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.config.JcrConfig;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.startup.StartableService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Repository;
import java.io.File;
import java.io.InputStream;

@Getter
@ApplicationScoped
public class RepositoryService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class.getName());

    private final WebdavConfig webdavConfig;
    private final JcrConfig jcrConfig;

    private Repository repository;

    @Inject
    public RepositoryService(
        WebdavConfig webdavConfig,
        JcrConfig jcrConfig
    ) {
        this.webdavConfig = webdavConfig;
        this.jcrConfig = jcrConfig;
    }

    @Override
    public void onStart() throws Exception {
        try {
            String repositoryHome = jcrConfig.getRepositoryHome();
            File homedir = new File(repositoryHome);
            if (!homedir.exists()) {
                homedir.mkdirs();
            }
            System.setProperty("webdav.home", webdavConfig.getRootFolder());
            System.setProperty("rep.home", repositoryHome);

            InputStream is = RepositoryService.class.getResourceAsStream("/repository.xml");
            RepositoryConfig config = RepositoryConfig.create(is, homedir.getAbsolutePath());
            this.repository = RepositoryImpl.create(config);

            log.info("Jackrabbit repository initialized in: " + repositoryHome);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Jackrabbit repository", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (repository instanceof RepositoryImpl) {
                log.info("Shutting down Jackrabbit repository...");
                ((RepositoryImpl) repository).shutdown();
                log.info("Jackrabbit repository shutdown complete");
            }
        } catch (Exception e) {
            log.error("Error shutting down repository", e);
        }
    }
}
