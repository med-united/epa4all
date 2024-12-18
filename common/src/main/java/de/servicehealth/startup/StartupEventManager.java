package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupEventManager {

    private static final Logger log = Logger.getLogger(StartupEventManager.class.getName());

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
                    log.log(Level.SEVERE, msg, e);
                    throw new Error(e);
                }
            });
    }
}
