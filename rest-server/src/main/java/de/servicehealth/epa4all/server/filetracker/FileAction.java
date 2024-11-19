package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;

public interface FileAction {

    String toString();

    String getTaskId();

    String getFileName();

    EpaContext getEpaContext();

    default boolean isUpload() {
        return this instanceof FileUpload;
    }
}
