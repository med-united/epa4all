package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ExternalEpaTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "external-epa-test";
    }
}
