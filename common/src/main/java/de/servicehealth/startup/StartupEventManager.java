package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

@ApplicationScoped
public class StartupEventManager {

    private static final Logger log = LoggerFactory.getLogger(StartupEventManager.class.getName());

    @Inject
    Instance<StartupEventListener> listeners;

    public void onStart(@Observes StartupEvent ev) {
        listeners.stream()
            .sorted(Comparator.comparingInt(StartupEventListener::getPriority))
            .forEach(listener -> {
                try {
                    listener.onStart(ev);
                } catch (Exception e) {
                    String msg = String.format(
                        "Error while starting %s -> %s", listener.getClass().getSimpleName(), e.getMessage()
                    );
                    log.error(msg, e);
                    throw new Error(e);
                }
            });
    }
}
