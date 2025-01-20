package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

public class MTLSTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "mTLS";
    }
}