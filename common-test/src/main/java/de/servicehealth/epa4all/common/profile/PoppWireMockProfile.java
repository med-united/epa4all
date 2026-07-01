package de.servicehealth.epa4all.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class PoppWireMockProfile implements QuarkusTestProfile {

    public static final int POPP_WIREMOCK_PORT = 9445;

    public static final String POPP_CLIENT_PATH = "/popp/token";
    public static final String POPP_VSDM_PATH = "/vsdm/xml";

    @Override
    public String getConfigProfile() {
        return "wiremock";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        String baseUrl = "http://localhost:" + POPP_WIREMOCK_PORT;
        return Map.of(
            "cetp.flow", "popp",
            "popp.client.url", baseUrl + POPP_CLIENT_PATH,
            "popp.vsdm.url", baseUrl + POPP_VSDM_PATH
        );
    }
}
