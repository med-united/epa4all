package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class IdpTssProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "idp-tss";
    }

    @Override public Map<String,String> getConfigOverrides() {
        return Map.of("idp.kind", "tss");
    }
}