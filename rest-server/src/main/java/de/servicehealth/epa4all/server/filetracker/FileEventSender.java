package de.servicehealth.epa4all.server.filetracker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class FileEventSender {

    @Inject
    Event<FileEvent> fileEvent;

    @Inject
    Event<WorkspaceEvent> workspaceEvent;


    public void sendAsync(FileEvent event) {
        fileEvent.fireAsync(event);
    }

    public void send(WorkspaceEvent event) {
        workspaceEvent.fire(event);
    }
}
