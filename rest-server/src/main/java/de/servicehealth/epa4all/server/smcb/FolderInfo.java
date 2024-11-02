package de.servicehealth.epa4all.server.smcb;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class FolderInfo {

    private final String path;
    private final String uuid;

    public FolderInfo(String path, String uuid) {
        this.path = path;
        this.uuid = uuid;
    }
}
