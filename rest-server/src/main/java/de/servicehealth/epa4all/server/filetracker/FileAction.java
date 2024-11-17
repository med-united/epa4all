package de.servicehealth.epa4all.server.filetracker;

public interface FileAction {

    String toString();

    default boolean isUpload() {
        return this instanceof FileUpload;
    }
}
