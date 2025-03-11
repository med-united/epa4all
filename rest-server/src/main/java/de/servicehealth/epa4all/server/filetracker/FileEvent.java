package de.servicehealth.epa4all.server.filetracker;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;
import java.util.List;

@Getter
@AllArgsConstructor
public class FileEvent {

    private final String telematikId;
    private final List<File> files;
}