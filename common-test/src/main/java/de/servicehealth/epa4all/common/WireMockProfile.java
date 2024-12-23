package de.servicehealth.epa4all.common;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WireMockProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "wiremock";
    }
}
