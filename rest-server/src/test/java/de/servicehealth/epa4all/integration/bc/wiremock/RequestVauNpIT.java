package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class RequestVauNpIT extends AbstractWiremockTest {

    @Test
    void vauNpProvisioningReloaded() {
        RestAssured.config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 600000));

        prepareVauStubs(List.of());

        given()
            .queryParams(Map.of("backends", "localhost"))
            .when()
            .get("/vau/reload")
            .then()
            .statusCode(200);

        Collection<VauFacade> vauFacades = registry.getInstances(VauFacade.class);
        assertTrue(vauFacades.stream().allMatch(f -> f.getStatus().toString().contains("OK")));
    }
}