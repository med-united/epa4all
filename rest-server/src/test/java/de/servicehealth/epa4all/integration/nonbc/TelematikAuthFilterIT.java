package de.servicehealth.epa4all.integration.nonbc;

import de.servicehealth.epa4all.common.profile.MTLSTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(MTLSTestProfile.class)
public class TelematikAuthFilterIT {

    private void setupSSL(String path, String password) {
        RestAssured.config = RestAssured.config()
            .sslConfig(RestAssured.config().getSSLConfig()
                .keyStore(path, password)
                .keystoreType("PKCS12"));
    }

    @Test
    public void testHealthEndpoint() {
        File projectDir = new File("").getAbsoluteFile().getParentFile();
        String path = projectDir.getAbsolutePath() + "/tls/server/trust-store/client/client.p12";
        setupSSL(path, "changeit");
        given()
            .relaxedHTTPSValidation()
            .when().get("https://localhost:8442/health")
            .then()
            .statusCode(200)
            .body(containsString("UP"));

        setupSSL("/some-client-keystore.p12", "passpass");
        assertThrows(SSLHandshakeException.class, () -> given()
            .relaxedHTTPSValidation()
            .when().get("https://localhost:8442/health")
            .then()
            .statusCode(200)
            .body(containsString("UP")));
    }
}