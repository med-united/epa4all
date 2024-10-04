package de.servicehealth.epa4all.common;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProxyTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "proxy";
    }
}
