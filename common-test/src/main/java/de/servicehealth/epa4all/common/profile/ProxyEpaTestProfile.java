package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProxyEpaTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "proxy-epa-test";
    }
}
