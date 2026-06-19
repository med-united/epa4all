package de.servicehealth.epa4all.server.cetp;

import java.util.Arrays;

public enum CETPHandlerType {
    Cardlink,
    Popp;

    public static CETPHandlerType from(String value) {
        return Arrays.stream(CETPHandlerType.values())
            .filter(t -> t.name().equalsIgnoreCase(value))
            .findFirst()
            .orElse(Cardlink);
    }
}
