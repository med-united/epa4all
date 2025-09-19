package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class IdpEpaProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "idp-epa";
    }

    @Override public Map<String,String> getConfigOverrides() {
        return Map.of("idp.kind", "epa");
    }
}