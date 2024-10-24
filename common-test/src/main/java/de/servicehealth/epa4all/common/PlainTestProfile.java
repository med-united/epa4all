package de.servicehealth.epa4all.common;

import io.quarkus.test.junit.QuarkusTestProfile;

public class PlainTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "plain-test";
    }
}