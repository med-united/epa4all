package de.servicehealth.epa4all.integration.nonbc;

import de.servicehealth.epa4all.common.profile.MTLSTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestProfile(MTLSTestProfile.class)
public class TelematikAuthFilterIT {

    @BeforeAll
    static void setup() {
        RestAssured.config = RestAssured.config()
            .sslConfig(RestAssured.config().getSSLConfig()
                .keyStore("../tls/server/trusted-store/client/client.p12", "changeit"));
    }

    @Test
    public void testHealthEndpoint() {
        System.setProperty(
            "javax.xml.transform.TransformerFactory",
            "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl"
        );
        given()
            .when().get("https://localhost:8443/health")
            .then()
            .statusCode(200)
            .body(containsString("UP"));
    }
}