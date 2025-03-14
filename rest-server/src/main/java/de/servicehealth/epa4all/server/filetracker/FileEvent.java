package de.servicehealth.epa4all.server.filetracker;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;

@Getter
@AllArgsConstructor
public class FileEvent {

    private final String telematikId;
    private final File file;
}