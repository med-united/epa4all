package de.servicehealth.epa4all.server.jcr;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp.SKIPPED_FILES;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;

@Getter
@ApplicationScoped
public class RepositoryService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class.getName());

    private final WorkspaceService workspaceService;
    private final IFolderService folderService;
    private final WebdavConfig webdavConfig;
    private final JcrConfig jcrConfig;

    private Repository repository;

    @Inject
    public RepositoryService(
        WorkspaceService workspaceService,
        IFolderService folderService,
        WebdavConfig webdavConfig,
        JcrConfig jcrConfig
    ) {
        this.workspaceService = workspaceService;
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
        this.jcrConfig = jcrConfig;
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
            this.repository = RepositoryImpl.create(config);


            SimpleCredentials credentials = new SimpleCredentials("admin", "admin".toCharArray());
            Session adminSession = repository.login(credentials);
            Arrays.stream(folderService.getTelematikFolders()).forEach(telematikFolder -> {
                try {
                    String telematikId = telematikFolder.getName();
                    workspaceService.createWorkspace(adminSession, telematikId);
                    importFilesIntoJCR(credentials, telematikFolder, telematikId);
                } catch (Exception e) {
                    log.error("Error while importing webdav into JCR repository", e);
                }
            });
            adminSession.logout();
            
            log.info("Jackrabbit repository initialized in: " + repositoryHome);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Jackrabbit repository", e);
        }
    }

    public void importFilesIntoJCR(
        SimpleCredentials credentials,
        File telematikFolder,
        String workspace
    ) throws RepositoryException {
        Session session = repository.login(credentials, workspace);
        Node rootNode = session.getRootNode();
        importDirectory(telematikFolder, rootNode, session);
        session.save();
        session.logout();
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
                }
                importDirectory(file, dirNode, session);
            } else {
                try {
                    parentNode.getNode(file.getName());
                } catch (PathNotFoundException e) {
                    Node fileNode = parentNode.addNode(file.getName(), "nt:file");
                    Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
                    try (FileInputStream fis = new FileInputStream(file)) {
                        Binary binary = session.getValueFactory().createBinary(fis);
                        contentNode.setProperty("jcr:data", binary);
                        contentNode.setProperty("jcr:mimeType", resolveMimeType(file.getName()));
                        contentNode.setProperty("jcr:lastModified", file.lastModified());
                    } catch (IOException io) {
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
