package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class RequestVauNpIT extends AbstractWiremockTest {

    @Inject
    BeanRegistry registry;

    @Test
    void vauNpProvisioningReloaded() throws Exception {
        RestAssured.config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 600000));

        prepareIdpStubs();
        prepareVauStubs();
        prepareKonnektorStubs();

        given()
            .queryParams(Map.of("backends", "localhost"))
            .when()
            .get("/vau/reload")
            .then()
            .statusCode(200);

        assertTrue(registry.getInstances(VauFacade.class).stream()
            .allMatch(f -> f.getStatus().toString().contains("OK"))
        );
    }
}

