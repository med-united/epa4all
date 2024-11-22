package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

public abstract class StartableService implements StartupEventListener {

    public static final int CxfClientFactoryPriority = 1000;
    public static final int MultiEpaPriority = 2000;
    public static final int VauNpProviderPriority = 3000;
    
    private final Logger log = Logger.getLogger(getClass().getSimpleName());

    @ConfigProperty(name = "startup-events.disabled", defaultValue = "false")
    boolean startupEventsDisabled;

    @Override
    public int getPriority() {
        return 2500;
    }

    public void onStart(StartupEvent ev) {
        String className = getClass().getSimpleName();
        if (startupEventsDisabled) {
            log.warning(String.format("[%s] STARTUP events are disabled by config property, initialization is SKIPPED", className));
        } else {
            long start = System.currentTimeMillis();
            onStart();
            long delta = System.currentTimeMillis() - start;
            log.info(String.format("[%s] STARTED in %d ms", className, delta));
        }
    }

    protected abstract void onStart();
}
