package de.servicehealth.epa4all.server.rest.fileserver.prop.type;

import lombok.Getter;

import static de.servicehealth.epa4all.server.entitlement.EntitlementFile.ENTITLEMENT_FILE;
import static de.servicehealth.epa4all.server.filetracker.ChecksumFile.CHECKSUM_FILE_NAME;

@Getter
public enum FileType {

    Mandatory(""),
    Checksum(CHECKSUM_FILE_NAME),
    Entitlement(ENTITLEMENT_FILE),
    Other("");

    private final String name;

    FileType(String name) {
        this.name = name;
    }

    public static FileType fromName(String name) {
        for(FileType fileType : values()) {
            if (fileType.getName().equals(name)) {
                return fileType;
            }
        }
        return Other;
    }
}
