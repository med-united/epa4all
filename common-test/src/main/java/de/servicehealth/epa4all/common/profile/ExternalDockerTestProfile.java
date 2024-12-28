package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ExternalDockerTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "external-docker-test";
    }
}