package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.filetracker.upload.FileRawUpload;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;

public interface FileAction {

    String toString();

    String getTaskId();

    String getKvnr();

    String getFileName();

    String getTelematikId();

    EpaContext getEpaContext();

    default boolean isUpload() {
        return this instanceof FileUpload || this instanceof FileRawUpload;
    }
}
