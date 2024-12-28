package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

public class PlainLocalTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "plain-local-test";
    }
}