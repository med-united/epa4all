package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.logging.Logger;

public abstract class StartableService implements StartupEventListener {

    public static final int CxfClientFactoryPriority = 1000;
    public static final int MultiEpaPriority = 2000;
    public static final int VauNpProviderPriority = 3000;
    
    private final Logger log = Logger.getLogger(getClass().getSimpleName());

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
        if (startupEventsDisabled) {
            log.warning(String.format("[%s] STARTUP events are disabled by config property, initialization is SKIPPED", className));
        } else {
            configDirectory = new File(configFolder);
            if (!configDirectory.exists() || !configDirectory.isDirectory()) {
                throw new IllegalStateException("Konnektor config directory is corrupted");
            }
            
            long start = System.currentTimeMillis();
            onStart();
            long delta = System.currentTimeMillis() - start;
            log.info(String.format("[%s] STARTED in %d ms", className, delta));
        }
    }

    protected abstract void onStart() throws Exception;
}
