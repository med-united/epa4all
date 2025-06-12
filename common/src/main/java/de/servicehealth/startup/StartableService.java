package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Getter
public abstract class StartableService implements StartupEventListener {

    public static final int CxfClientFactoryPriority = 1000;
    public static final int MultiEpaPriority = 2000;
    public static final int VauSessionsJobPriority = 4000;
    public static final int VauNpProviderPriority = 5000;

    private final Logger log = LoggerFactory.getLogger(getClass().getSimpleName());

    @Inject
    StartupConfig startupConfig;

    protected File configDirectory;

    @Override
    public int getPriority() {
        return 2500;
    }

    public void onStart(StartupEvent ev) throws Exception {
        String className = getClass().getSimpleName();
        configDirectory = new File(startupConfig.getConfigFolder());
        if (!configDirectory.exists() || !configDirectory.isDirectory()) {
            String configPath = configDirectory.getAbsolutePath();
            String msg = "Konnektor config directory is not correct. Should exist as directory here: " + configPath;
            throw new IllegalStateException(msg);
        }
        if (startupConfig.isStartupEventsDisabled()) {
            log.warn(String.format("[%s] STARTUP events are disabled by config property, initialization is SKIPPED", className));
        } else {
            long start = System.currentTimeMillis();
            doStart();
            long delta = System.currentTimeMillis() - start;
            log.info(String.format("[%s] STARTED in %d ms", className, delta));
        }
    }

    protected abstract void doStart() throws Exception;
}
