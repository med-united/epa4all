package de.servicehealth.epa4all.server.jcr;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.jcr.SimpleCredentials;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.DEFAULT_AUTHENTICATE_HEADER;
import static java.io.File.separator;

@Getter
@ApplicationScoped
public class JcrConfig {

    @ConfigProperty(name = "jcr.repository.home")
    File repositoryHome;

    @ConfigProperty(name = "jcr.config.path")
    File configPath;

    @ConfigProperty(name = "jcr.resource-path-prefix")
    String resourcePathPrefix;

    @ConfigProperty(name = "jcr.missing-auth-mapping")
    String missingAuthMapping;

    @ConfigProperty(name = "jcr.repository.reinit.attempts", defaultValue = "1")
    int reInitAttempts;

    // see JCRParams.INIT_PARAM_CSRF_PROTECTION
    @ConfigProperty(name = "jcr.csrf-protection")
    Optional<String> csrfProtection;

    @ConfigProperty(name = "jcr.authenticate-header", defaultValue = DEFAULT_AUTHENTICATE_HEADER)
    String authenticateHeader;

    @ConfigProperty(name = "jcr.create.absolute.uri", defaultValue = "true")
    boolean createAbsoluteURI;

    @ConfigProperty(name = "jcr.mixin.config")
    Map<String, String> mixinMap;

    private volatile Map<String, List<String>> mixinConfigMap;

    public String getWorkspacesHome() {
        return repositoryHome.getAbsolutePath() + separator + "workspaces";
    }

    public SimpleCredentials getCredentials() {
        String[] parts = getMissingAuthMapping().split(":");
        String user = parts[0];
        String pass = parts[1];
        return new SimpleCredentials(user, pass.toCharArray());
    }

    public Map<String, List<String>> getMixinMap() {
        if (mixinConfigMap == null) {
            mixinConfigMap = mixinMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> Arrays.stream(e.getValue().split(",")).filter(s -> !s.isEmpty()).toList()
            ));
        }
        return mixinConfigMap;
    }
}