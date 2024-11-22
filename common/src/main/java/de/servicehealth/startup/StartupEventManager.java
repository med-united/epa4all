package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;

@ApplicationScoped
public class StartupEventManager {

    @Inject
    Instance<StartupEventListener> listeners;

    public void onStart(@Observes StartupEvent ev) {
        listeners.stream()
            .sorted(Comparator.comparingInt(StartupEventListener::getPriority))
            .forEach(listener -> listener.onStart(ev));
    }
}
