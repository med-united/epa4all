package de.servicehealth.epa4all.server.jcr;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.Optional;

import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.DEFAULT_AUTHENTICATE_HEADER;

@Getter
@ApplicationScoped
public class JcrConfig {

    @ConfigProperty(name = "jcr.repository.home")
    File repositoryHome;

    @ConfigProperty(name = "jcr.resource-path-prefix")
    String resourcePathPrefix;

    @ConfigProperty(name = "jcr.missing-auth-mapping")
    String missingAuthMapping;

    // see JCRParams.INIT_PARAM_CSRF_PROTECTION
    @ConfigProperty(name = "jcr.csrf-protection")
    Optional<String> csrfProtection;

    @ConfigProperty(name = "jcr.authenticate-header", defaultValue = DEFAULT_AUTHENTICATE_HEADER)
    String authenticateHeader;

    @ConfigProperty(name = "jcr.create.absolute.uri", defaultValue = "true")
    boolean createAbsoluteURI;

    public String getWorkspacesHome() {
        return repositoryHome.getAbsolutePath() + "/workspaces";
    }
}