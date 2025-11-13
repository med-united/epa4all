package de.servicehealth.epa4all.integration.bc.epa;

import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_SMCB_ICCSN;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class TelematikByIccsnRISEIT {

    @Test
    public void telematikIdReturnedByIccsn() {
        String iccsn = "80276883110000147793";
        given()
            .header(X_SMCB_ICCSN, iccsn)
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of("iccsn", iccsn)) // SMC-B
            .when()
            .get("/telematik/id")
            .then()
            .body(equalTo("3-SMC-B-Testkarte--883110000147793"))
            .statusCode(200);
    }
}
