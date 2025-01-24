package de.servicehealth.epa4all.common;

import java.nio.file.Path;

@FunctionalInterface
public interface TmpFolderAction {

    void execute(Path tmpFolder) throws Exception;
}
