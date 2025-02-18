package de.servicehealth.epa4all.integration.bc.epa;

import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class TelematikByIccsnIT {

    @Test
    public void telematikIdReturnedByIccsn() {
        given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of("iccsn", "80276883110000141773")) // SMC-B
            .when()
            .get("/telematik/id")
            .then()
            .body(containsString("3-SMC-B-Testkarte--883110000147807"))
            .statusCode(200);
    }
}
