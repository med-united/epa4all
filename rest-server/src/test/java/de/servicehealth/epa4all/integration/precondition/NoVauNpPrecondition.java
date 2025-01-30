package de.servicehealth.epa4all.integration.precondition;

import de.servicehealth.epa4all.server.idp.vaunp.VauNpFile;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NoVauNpPrecondition implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = Logger.getLogger(NoVauNpPrecondition.class.getName());

    @Override
    public Map<String, String> start() {
        String configFolder = ConfigProvider.getConfig().getValue("ere.per.konnektor.config.folder", String.class);
        try {
            new VauNpFile(new File(configFolder)).reset();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while NoVauNpPrecondition.start()", e);
        }
        return Map.of();
    }

    @Override
    public void stop() {
    }
}
