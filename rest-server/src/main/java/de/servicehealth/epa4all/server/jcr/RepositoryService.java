package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.startup.StartableService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_MIXIN_NAME;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp.SKIPPED_FILES;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;

@Getter
@ApplicationScoped
public class RepositoryService extends StartableService {

    public static final String ROOT_FOLDER_NODE = "rootFolder";

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class.getName());

    private final WorkspaceService workspaceService;
    private final IFolderService folderService;
    private final WebdavConfig webdavConfig;
    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    private final SimpleCredentials credentials;

    private Repository repository;

    @Inject
    public RepositoryService(
        WorkspaceService workspaceService,
        IFolderService folderService,
        WebdavConfig webdavConfig,
        PropBuilder propBuilder,
        JcrConfig jcrConfig
    ) {
        this.workspaceService = workspaceService;
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
        this.propBuilder = propBuilder;
        this.jcrConfig = jcrConfig;

        String[] parts = jcrConfig.getMissingAuthMapping().split(":");
        String user = parts[0];
        String pass = parts[1];
        credentials = new SimpleCredentials(user, pass.toCharArray());
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

            Arrays.stream(folderService.getTelematikFolders()).forEach(telematikFolder -> {
                String telematikId = telematikFolder.getName();
                workspaceService.createWorkspace(telematikId);
                importFilesIntoWorkspace(telematikFolder, telematikId);
            });

            log.info("Jackrabbit repository initialized in: " + repositoryHome);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Jackrabbit repository", e);
        }
    }

    public void importFilesIntoWorkspace(File telematikFolder, String workspaceName) {
        try {
            Session session = repository.login(credentials, workspaceName);
            try {
                Node rootNode = session.getRootNode();
                importDirectory(telematikFolder, rootNode.getNode(ROOT_FOLDER_NODE), session);
            } catch (Exception e) {
                session.refresh(false);
                throw e;
            } finally {
                session.logout();
            }
        } catch (Exception e) {
            log.error("Error while importing files into workspace, name = " + workspaceName, e);
        }
    }

    private void importDirectory(File dir, Node parentNode, Session session) throws RepositoryException {
        List<File> files = getFiles(dir);
        for (File file : files) {
            if (file.isDirectory()) {
                Node dirNode;
                try {
                    dirNode = parentNode.getNode(file.getName());
                } catch (PathNotFoundException e) {
                    dirNode = parentNode.addNode(file.getName(), "nt:folder");
                    if (dirNode.canAddMixin(EPA_MIXIN_NAME)) {
                        dirNode.addMixin(EPA_MIXIN_NAME);
                    }
                    try {
                        propBuilder.setEpaProps(file, dirNode);
                        session.save();
                    } catch (Exception ex) {
                        log.error("Error while setEpaProps", ex);
                    }
                }
                importDirectory(file, dirNode, session);
            } else {
                try {
                    parentNode.getNode(file.getName());

                    //todo update lastModified ?
                    
                } catch (PathNotFoundException e) {
                    Node fileNode = parentNode.addNode(file.getName(), "nt:file");
                    Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
                    if (fileNode.canAddMixin(EPA_MIXIN_NAME)) {
                        fileNode.addMixin(EPA_MIXIN_NAME);
                    }
                    try (FileInputStream fis = new FileInputStream(file)) {
                        Binary binary = session.getValueFactory().createBinary(fis);
                        contentNode.setProperty("jcr:data", binary);
                        contentNode.setProperty("jcr:mimeType", resolveMimeType(file.getName()));
                        propBuilder.setEpaProps(file, fileNode);
                        session.save();
                    } catch (Exception io) {
                        throw new RepositoryException("Failed to import file: " + file, io);
                    }
                }
            }
        }
    }

    private List<File> getFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null
            ? List.of()
            : Stream.of(files).filter(f -> SKIPPED_FILES.stream().noneMatch(s -> s.equals(f.getName()))).toList();
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
