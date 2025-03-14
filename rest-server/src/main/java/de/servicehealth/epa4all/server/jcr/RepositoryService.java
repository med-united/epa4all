package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.filetracker.FileEvent;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.startup.StartableService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_NAMESPACE_PREFIX;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_NAMESPACE_URI;
import static de.servicehealth.utils.ServerUtils.getPathParts;

@Getter
@ApplicationScoped
public class RepositoryService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class.getName());

    private final IFolderService folderService;
    private final WebdavConfig webdavConfig;
    private final TypesService typesService;
    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    private final SimpleCredentials credentials;

    private Repository repository;

    @Inject
    public RepositoryService(
        IFolderService folderService,
        WebdavConfig webdavConfig,
        TypesService typesService,
        PropBuilder propBuilder,
        JcrConfig jcrConfig
    ) {
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
        this.typesService = typesService;
        this.propBuilder = propBuilder;
        this.jcrConfig = jcrConfig;

        credentials = jcrConfig.getCredentials();
    }

    private void registerTypes() throws Exception {
        Session session = repository.login(credentials);
        typesService.registerNamespace(session, EPA_NAMESPACE_PREFIX, EPA_NAMESPACE_URI);
        typesService.registerEpaMixin(session);
        session.save();
        session.logout();
    }

    @Override
    public void onStart() throws Exception {
        try {
            File repositoryHome = jcrConfig.getRepositoryHome();
            if (!repositoryHome.exists()) {
                repositoryHome.mkdirs();
            }
            System.setProperty("workspaces.home", jcrConfig.getWorkspacesHome());
            System.setProperty("repository.home", repositoryHome.getAbsolutePath());

            InputStream is = RepositoryService.class.getResourceAsStream("/repository.xml");
            RepositoryConfig config = RepositoryConfig.create(is, repositoryHome.getAbsolutePath());
            repository = RepositoryImpl.create(config);

            registerTypes();

            Arrays.stream(folderService.getTelematikFolders()).forEach(f ->
                new Workspace(repository, jcrConfig, propBuilder).createOrUpdate(f)
            );

            log.info("Jackrabbit repository initialized in: " + repositoryHome);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Jackrabbit repository", e);
        }
    }

    public void createOrUpdateWorkspace(File telematikFolder) {
        new Workspace(repository, jcrConfig, propBuilder).createOrUpdate(telematikFolder);
    }

    public void handleFileEvent(@Observes FileEvent fileEvent) {
        try {
            Session session = repository.login(credentials, fileEvent.getTelematikId());
            Node rootNode = session.getRootNode();
            try {
                File file = fileEvent.getFile();
                List<String> pathParts = getPathParts(file.getPath());
                String relPath = String.join("/", pathParts.subList(pathParts.indexOf("webdav") + 2, pathParts.size() - 1));
                Node parentNode = rootNode.getNode("rootFolder/" + relPath);
                Node contentNode = propBuilder.handleFile(session, parentNode, file);

                check(contentNode);
            } catch (Exception e) {
                session.refresh(false);
                throw e;
            } finally {
                session.logout();
            }
        } catch (Exception e) {
            log.error("Error while handling fileEvent for " + fileEvent.getFile().getAbsolutePath(), e);
        }
    }

    private void check(Node contentNode) throws Exception {
        Binary binary = contentNode.getProperty("jcr:data").getBinary();

        try (InputStream is = binary.getStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("PersoenlicheVersichertendaten")) {
                    found = true;
                    System.out.println("Text found in binary content");
                    break;
                }
            }

            if (!found) {
                System.out.println("Text NOT found in binary content");
            }
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