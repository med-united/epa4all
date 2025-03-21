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
    public static final int KonnektorClientPriority = 3000;
    public static final int VauSessionsJobPriority = 4000;
    public static final int VauNpProviderPriority = 5000;

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
        String className = getClass().getSimpleName();
        configDirectory = new File(configFolder);
        if (configDirectory.exists() && configDirectory.isDirectory()) {
            if (startupEventsDisabled) {
                log.warn(String.format("[%s] STARTUP events are disabled by config property, initialization is SKIPPED", className));
            } else {
                long start = System.currentTimeMillis();
                onStart();
                long delta = System.currentTimeMillis() - start;
                log.info(String.format("[%s] STARTED in %d ms", className, delta));
            }
        } else {
            String configPath = configDirectory != null ? configDirectory.getAbsolutePath() : "null";
            String msg = "Konnektor config directory is not correct. Should exist as directory here: " + configPath;
            throw new IllegalStateException(msg);
        }
    }

    protected abstract void onStart() throws Exception;
}
