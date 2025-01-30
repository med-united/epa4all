package de.servicehealth.epa4all.server.rest.fileserver.prop.type;

import lombok.Getter;

@Getter
public enum DirectoryType {

    Mandatory(-1),
    Root(0),
    Telematik(1),
    Insurant(2),
    Category(3);

    private final int level;

    DirectoryType(int level) {
        this.level = level;
    }

    public static DirectoryType fromLevel(int level) {
        for(DirectoryType directoryType : values()) {
            if (directoryType.getLevel() == level) {
                return directoryType;
            }
        }
        throw new IllegalArgumentException("DirectoryType unsupported level: " + level);
    }
}
