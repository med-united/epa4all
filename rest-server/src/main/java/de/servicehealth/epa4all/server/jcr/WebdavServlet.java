package de.servicehealth.epa4all.server.jcr;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import org.apache.jackrabbit.webdav.jcr.JCRWebdavServerServlet;

import javax.jcr.Repository;
import java.io.Serial;

@WebServlet(
    urlPatterns = "/webdav2",
    initParams = {
        @WebInitParam(name = "resource-path-prefix", value = "/webdav2"),
        @WebInitParam(name = "repository-prefix", value = "repository")
    }
)
public class WebdavServlet extends JCRWebdavServerServlet {

    @Serial
    private static final long serialVersionUID = -1743484209610424817L;
    
    @Inject
    RepositoryService repositoryService;

    @Override
    public Repository getRepository() {
        return repositoryService.getRepository();
    }
}
