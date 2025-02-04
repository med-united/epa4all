package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public abstract class StartableService implements StartupEventListener {

    public static final int CxfClientFactoryPriority = 1000;
    public static final int MultiEpaPriority = 2000;
    public static final int VauNpProviderPriority = 3000;
    
    private final Logger log = LoggerFactory.getLogger(getClass().getSimpleName());

    @ConfigProperty(name = "startup-events.disabled", defaultValue = "false")
    boolean startupEventsDisabled;

    @Setter
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

    protected File configDirectory;

    @Override
    public int getPriority() {
        return 2500;
    }

    public void onStart(StartupEvent ev) throws Exception {
        configDirectory = new File(configFolder);
        if (configDirectory.exists() && configDirectory.isDirectory()) {
            if (startupEventsDisabled) {
                log.warn("STARTUP events are disabled by config property, initialization is SKIPPED");
            } else {
                long start = System.currentTimeMillis();
                onStart();
                long delta = System.currentTimeMillis() - start;
                log.info(String.format("STARTED in %d ms", delta));
            }
        } else {
            throw new IllegalStateException("Konnektor config directory is not correct. Should exist as directory here: "+(configDirectory != null ? configDirectory.getAbsolutePath() : "null"));
        }
    }

    protected abstract void onStart() throws Exception;
}
