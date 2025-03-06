package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import java.io.File;
import java.util.Arrays;

@Dependent
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class.getName());
    
    private final WebdavConfig webdavConfig;
    private final RepositoryService repositoryService;

    @Inject
    public WorkspaceService(
        WebdavConfig webdavConfig,
        RepositoryService repositoryService
    ) {
        this.webdavConfig = webdavConfig;
        this.repositoryService = repositoryService;
    }

    public void createWorkspace(String telematikId, String template) throws Exception {
        Repository repository = repositoryService.getRepository();
        Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        try {
            Workspace workspace = adminSession.getWorkspace();
            boolean exists = Arrays.asList(workspace.getAccessibleWorkspaceNames()).contains(telematikId);
            if (!exists) {
                File workspaceHome = new File(webdavConfig.getRootFolder() + "/" + telematikId);
                if (!workspaceHome.exists()) {
                    workspaceHome.mkdirs();
                }
                workspace.createWorkspace(telematikId, template);
                log.info("Workspace '" + telematikId + "' created successfully!");
            } else {
                log.info("Workspace '" + telematikId + "' already exists");
            }
        } catch (Exception e) {
            log.error("Error while creating workspace", e);
        } finally {
            adminSession.logout();
        }
    }
}
