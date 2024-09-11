package de.servicehealth.epa4all.common;

import io.quarkus.test.junit.QuarkusTestProfile;

public class DevTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "dev";
    }
}
