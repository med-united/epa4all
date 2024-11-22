package de.servicehealth.startup;

import io.quarkus.runtime.StartupEvent;

public interface StartupEventListener {

    int getPriority();

    void onStart(StartupEvent ev);
}