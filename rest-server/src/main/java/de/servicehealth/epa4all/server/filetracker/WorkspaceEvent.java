package de.servicehealth.epa4all.server.filetracker;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;

@Getter
@AllArgsConstructor
public class WorkspaceEvent {

    private final File telematikFolder;
}
