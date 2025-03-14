package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.propsource.PropBuilder;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp.SKIPPED_FILES;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Workspace {

    private static final Logger log = LoggerFactory.getLogger(Workspace.class.getName());

    public static final String ROOT_FOLDER_NODE = "rootFolder";

    private final SimpleCredentials credentials;
    private final PropBuilder propBuilder;
    private final Repository repository;
    private final JcrConfig jcrConfig;

    public Workspace(Repository repository, JcrConfig jcrConfig, PropBuilder propBuilder) {
        this.repository = repository;
        this.jcrConfig = jcrConfig;
        this.propBuilder = propBuilder;

        credentials = jcrConfig.getCredentials();
    }

    private InputSource getInputSource(String telematikId) {
        String path = jcrConfig.getWorkspacesHome() + "/" + telematikId;
        String configXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Workspace name="%s">
                <FileSystem class="org.apache.jackrabbit.core.fs.local.LocalFileSystem">
                    <param name="path" value="%s"/>
                </FileSystem>
                <PersistenceManager class="org.apache.jackrabbit.core.persistence.bundle.BundleFsPersistenceManager"/>
                <SearchIndex class="org.apache.jackrabbit.core.query.lucene.SearchIndex">
                    <param name="path" value="%s/index"/>
                    <param name="supportHighlighting" value="true"/>
                </SearchIndex>
            </Workspace>""".formatted(telematikId, path, path);

        return new InputSource(new ByteArrayInputStream(configXml.getBytes(UTF_8)));
    }

    public void createOrUpdate(File telematikFolder) {
        String telematikId = telematikFolder.getName();
        try {
            Session session = repository.login(credentials);
            boolean created = false;
            try {
                javax.jcr.Workspace workspace = session.getWorkspace();
                if (Arrays.asList(workspace.getAccessibleWorkspaceNames()).contains(telematikId)) {
                    log.info("Workspace '" + telematikId + "' already exists");
                } else {
                    if (workspace instanceof JackrabbitWorkspace jackrabbitWorkspace) {
                        InputSource inputSource = getInputSource(telematikId);
                        jackrabbitWorkspace.createWorkspace(telematikId, inputSource);
                        session.save();
                        created = true;
                        log.info("Workspace '" + telematikId + "' created successfully!");
                    }
                }
            } catch (Exception e) {
                session.refresh(false);
                log.error("Error while creating workspace [" + telematikId + "]", e);
            } finally {
                session.logout();
            }
            if (created) {
                session = repository.login(credentials, telematikId);
                try {
                    Node root = session.getRootNode();
                    propBuilder.handleFolder(session, root, telematikFolder, ROOT_FOLDER_NODE);
                } catch (Exception e) {
                    session.refresh(false);
                    log.error("Error while creating folder node [" + telematikId + "]", e);
                } finally {
                    session.logout();
                }
            }
            importFilesIntoWorkspace(telematikFolder, telematikId);
        } catch (Exception e) {
            log.error("Error while creating workspace, name = " + telematikId, e);
        }
    }

    private void importFilesIntoWorkspace(File telematikFolder, String workspaceName) {
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
                Node folderNode = propBuilder.handleFolder(session, parentNode, file, file.getName());
                importDirectory(file, folderNode, session);
            } else {
                propBuilder.handleFile(session, parentNode, file);
            }
        }
    }

    private List<File> getFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null
            ? List.of()
            : Stream.of(files).filter(f -> SKIPPED_FILES.stream().noneMatch(s -> s.equals(f.getName()))).toList();
    }
}