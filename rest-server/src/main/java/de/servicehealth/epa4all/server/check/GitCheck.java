package de.servicehealth.epa4all.server.check;

import de.health.service.check.Check;
import de.health.service.check.Status;
import de.health.service.config.api.IRuntimeConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

@ApplicationScoped
public class GitCheck implements Check {

    private static final Logger log = LoggerFactory.getLogger(GitCheck.class.getName());

    private static final String GIT_COMMIT_ID = "git-commit";
    private static final String BUILD_TIME = "build-time";

    private static final Properties properties = new Properties();
    static {
        try (InputStream inputStream = GitCheck.class.getResourceAsStream("/git.properties")) {
            properties.load(inputStream);
        } catch (Exception e) {
            log.error("Error while loading git.properties", e);
        }
    }

    void onStart(@Observes StartupEvent ev) {
        log.info("{}: {}", BUILD_TIME, properties.getProperty(BUILD_TIME));
        log.info("{}: {}", GIT_COMMIT_ID, properties.getProperty(GIT_COMMIT_ID));
    }

    @Override
    public String getName() {
        return GIT_CHECK;
    }

    @Override
    public Status getStatus(IRuntimeConfig runtimeConfig) {
        return Status.Up200;
    }

    @Override
    public Map<String, Object> getData(IRuntimeConfig runtimeConfig) {
        return Map.of(
            BUILD_TIME, properties.getProperty(BUILD_TIME),
            GIT_COMMIT_ID, properties.getProperty(GIT_COMMIT_ID)
        );
    }
}
