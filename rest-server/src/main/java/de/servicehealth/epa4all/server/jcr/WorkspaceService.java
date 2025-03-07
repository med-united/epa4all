package de.servicehealth.epa4all.server.jcr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class.getName());

    private final JcrConfig jcrConfig;
    private final RepositoryService repositoryService;

    @Inject
    public WorkspaceService(
        JcrConfig jcrConfig,
        RepositoryService repositoryService
    ) {
        this.jcrConfig = jcrConfig;
        this.repositoryService = repositoryService;
    }

    public void createWorkspace(String telematikId) throws Exception {
        Repository repository = repositoryService.getRepository();
        Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        try {
            createWorkspace(adminSession, telematikId);
        } finally {
            adminSession.logout();
        }
    }

    public void createWorkspace(Session adminSession, String telematikId) {
        try {
            Workspace workspace = adminSession.getWorkspace();
            boolean exists = Arrays.asList(workspace.getAccessibleWorkspaceNames()).contains(telematikId);
            if (!exists) {
                if (workspace instanceof JackrabbitWorkspace jackrabbitWorkspace) {
                    InputSource inputSource = getInputSource(telematikId);
                    jackrabbitWorkspace.createWorkspace(telematikId, inputSource);
                    log.info("Workspace '" + telematikId + "' created successfully!");
                }
            } else {
                log.info("Workspace '" + telematikId + "' already exists");
            }
        } catch (Exception e) {
            log.error("Error while creating workspace", e);
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
