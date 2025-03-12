package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

import static de.servicehealth.epa4all.server.jcr.RepositoryService.ROOT_FOLDER_NODE;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_MIXIN_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class.getName());

    private final RepositoryService repositoryService;
    private final FolderService folderService;
    private final TypesService typesService;
    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    private final SimpleCredentials credentials;

    @Inject
    public WorkspaceService(
        RepositoryService repositoryService,
        FolderService folderService,
        TypesService typesService,
        PropBuilder propBuilder,
        JcrConfig jcrConfig
    ) {
        this.repositoryService = repositoryService;
        this.folderService = folderService;
        this.typesService = typesService;
        this.propBuilder = propBuilder;
        this.jcrConfig = jcrConfig;

        String[] parts = jcrConfig.getMissingAuthMapping().split(":");
        String user = parts[0];
        String pass = parts[1];
        credentials = new SimpleCredentials(user, pass.toCharArray());
    }

    public void createWorkspace(String telematikId) {
        Repository repository = repositoryService.getRepository();
        try {
            Session session = repository.login(credentials);
            typesService.registerEpaMixin(session);
            boolean created = false;
            try {
                Workspace workspace = session.getWorkspace();
                created = Arrays.asList(workspace.getAccessibleWorkspaceNames()).contains(telematikId);
                if (created) {
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
                    Node rootNode = root.addNode(ROOT_FOLDER_NODE, "nt:folder");
                    if (rootNode.canAddMixin(EPA_MIXIN_NAME)) {
                        rootNode.addMixin(EPA_MIXIN_NAME);
                    }
                    File telematikFolder = folderService.getTelematikFolder(telematikId);
                    propBuilder.setEpaProps(telematikFolder, rootNode);

                    session.save();
                } catch (Exception e) {
                    session.refresh(false);
                    log.error("Error while creating folder node [" + telematikId + "]", e);
                } finally {
                    session.logout();
                }
            }
        } catch (Exception e) {
            log.error("Error while creating workspace, name = " + telematikId, e);
        }
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
}
