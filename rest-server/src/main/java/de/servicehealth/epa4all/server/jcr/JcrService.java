package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.filetracker.FileEvent;
import de.servicehealth.epa4all.server.filetracker.WorkspaceEvent;
import de.servicehealth.epa4all.server.jcr.mixin.MixinContext;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.startup.StartableService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeTypeManager;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import static de.servicehealth.epa4all.server.jcr.Workspace.ROOT_FOLDER_NODE;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_NAMESPACE_URI;
import static de.servicehealth.epa4all.server.jcr.prop.MixinProp.EPA_NAMESPACE_PREFIX;
import static de.servicehealth.utils.ServerUtils.getPathParts;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@ApplicationScoped
public class JcrService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(JcrService.class.getName());

    private static final MixinContext EMPTY_MIXIN_CONTEXT = new MixinContext(List.of());

    private final JcrRepositoryProvider repositoryProvider;
    private final IFolderService folderService;
    private final TypesService typesService;
    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    private final SimpleCredentials credentials;

    private int reInitAttempts;

    @Inject
    public JcrService(
        JcrRepositoryProvider repositoryProvider,
        IFolderService folderService,
        TypesService typesService,
        PropBuilder propBuilder,
        JcrConfig jcrConfig
    ) {
        this.repositoryProvider = repositoryProvider;
        this.folderService = folderService;
        this.typesService = typesService;
        this.propBuilder = propBuilder;
        this.jcrConfig = jcrConfig;

        credentials = jcrConfig.getCredentials();
    }

    @Override
    public void onStart() throws Exception {
        System.setProperty("disableCheckForReferencesInContentException", "true");
        File repositoryHome = jcrConfig.getRepositoryHome();
        if (!repositoryHome.exists()) {
            repositoryHome.mkdirs();
        }
        String repositoryPath = repositoryHome.getAbsolutePath();
        try {
            System.setProperty("workspaces.home", jcrConfig.getWorkspacesHome());
            System.setProperty("repository.home", repositoryPath);

            InputStream is = JcrService.class.getResourceAsStream("/jcr/repository.xml");
            RepositoryConfig config = RepositoryConfig.create(is, repositoryPath);
            Repository repository = RepositoryImpl.create(config);
            repositoryProvider.setRepository(repository);

            Session defaultWsSession = repository.login(credentials);
            typesService.registerNamespace(defaultWsSession, EPA_NAMESPACE_PREFIX, EPA_NAMESPACE_URI);
            MixinContext mixinContext = typesService.trackEpaMixins(defaultWsSession);

            for (File telematikFolder : folderService.getTelematikFolders()) {
                processWorkspace(defaultWsSession, telematikFolder, mixinContext);
            }

            NodeTypeManager typeManager = defaultWsSession.getWorkspace().getNodeTypeManager();
            for (String staleMixin : mixinContext.getStaleMixins()) {
                typeManager.unregisterNodeType(staleMixin);
            }

            defaultWsSession.save();
            defaultWsSession.logout();

            log.info("Jackrabbit repository initialized in: " + repositoryHome);
        } catch (Throwable e) {
            if (reInitAttempts++ >= jcrConfig.getReInitAttempts()) {
                String msg = String.format(
                    "Unable to init JCR repository after %d re-init attempts: %s", jcrConfig.getReInitAttempts(), e.getMessage()
                );
                throw new IllegalStateException(msg, e);
            }
            log.error("Failed to initialize Jackrabbit repository, home folder will be recreated", e);
            try {
                shutdown();
                deleteDirectory(repositoryHome);
            } catch (Throwable io) {
                log.error("Error while deleting JCR repository.home: " + repositoryPath, io);
            }
            onStart();
        }
    }

    private void processWorkspace(
        Session defaultWsSession,
        File telematikFolder,
        MixinContext mixinContext
    ) throws RepositoryException {
        String telematikId = telematikFolder.getName();
        Workspace workspace = new Workspace(jcrConfig, propBuilder);
        boolean created = workspace.getOrCreate(defaultWsSession, telematikId);
        Session session = repositoryProvider.getRepository().login(credentials, telematikId);
        try {
            workspace.init(session, telematikFolder, mixinContext, created);
            Node parentNode = session.getRootNode().getNode(ROOT_FOLDER_NODE);
            boolean staleMixins = !mixinContext.getStaleMixins().isEmpty();
            workspace.importFiles(telematikFolder, parentNode, session, staleMixins);
        } catch (RepositoryException e) {
            session.refresh(false);
            throw e;
        } finally {
            session.logout();
        }
    }

    void handleWorkspaceEvent(@Observes WorkspaceEvent workspaceEvent) {
        File telematikFolder = workspaceEvent.getTelematikFolder();
        try {
            Session defaultWsSession = repositoryProvider.getRepository().login(credentials);
            try {
                processWorkspace(defaultWsSession, telematikFolder, EMPTY_MIXIN_CONTEXT);
                defaultWsSession.save();
            } finally {
                defaultWsSession.logout();
            }
        } catch (RepositoryException e) {
            log.error("Error while createOrUpdateWorkspace: " + telematikFolder.getName(), e);
        }
    }

    // todo FileEvent.action [ CREATE | DELETE ]
    void handleFileEvent(@ObservesAsync FileEvent fileEvent) {
        String telematikId = fileEvent.getTelematikId();
        try {
            Session session = repositoryProvider.getRepository().login(credentials, telematikId);
            Node rootNode = session.getRootNode();
            try {
                for (File file : fileEvent.getFiles()) {
                    try {
                        List<String> pathParts = getPathParts(file.getPath());
                        String relPath = String.join("/", pathParts.subList(pathParts.indexOf("webdav") + 2, pathParts.size() - 1));
                        Node parentNode = rootNode.getNode("rootFolder/" + relPath);

                        // At moment only new file is handling here
                        // todo: сover cases for existing file modifications if any appear
                        propBuilder.handleFile(session, parentNode, file, false);
                        propBuilder.adjustParentFolders(session, parentNode, file);
                    } catch (RepositoryException e) {
                        session.refresh(false);
                        log.error("Error while handling fileEvent for " + file.getAbsolutePath(), e);
                    }
                }
            } finally {
                session.logout();
            }
        } catch (Throwable t) {
            log.error("Error while handling fileEvent for workspace: " + telematikId, t);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (repositoryProvider.getRepository() instanceof RepositoryImpl repository) {
                log.info("Shutting down Jackrabbit repository...");
                repository.shutdown();
                log.info("Jackrabbit repository shutdown complete");
            }
        } catch (Throwable e) {
            log.error("Error while JCR repository shutdown", e);
        }
    }
}