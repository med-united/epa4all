package de.servicehealth.epa4all.server.rest.fileserver.paging;

import java.util.Arrays;

public enum SortBy {

    Latest,
    Earliest;

    public static SortBy from(String value) {
        return Arrays.stream(values())
            .filter(s -> s.name().equalsIgnoreCase(value))
            .findFirst()
            .orElse(Latest);
    }
}
