package de.servicehealth.epa4all.integration.nonbc;

import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class KonnektorConfigsIT {

    @Test
    public void konnektorConfigsAreExposed() {
        Response response = given().header(ACCEPT, APPLICATION_XML).when().get("/konnektor/configs");
        assertEquals(200, response.getStatusCode());
        String xml = response.asString();
        assertTrue(xml.contains("KonnektorConfig"));
        assertTrue(xml.contains("clientCertificate"));

        response = given().header(ACCEPT, APPLICATION_JSON).when().get("/konnektor/configs");
        assertEquals(200, response.getStatusCode());
        String json = response.asString();
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("clientCertificate"));

        response = given()
            .header(ACCEPT, APPLICATION_JSON)
            .queryParams(Map.of(X_KONNEKTOR, "10.0.0.1"))
            .when()
            .get("/konnektor/configs");

        assertEquals(200, response.getStatusCode());
        json = response.asString();
        assertEquals("[]", json);
    }
}
