package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpFile;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpKey;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class RequestVauNpIT extends AbstractWiremockTest {

    @Test
    void vauNpProvisioningReloaded() throws Exception {
        VauNpFile vauNpFile = new VauNpFile(configFolder);
        vauNpFile.reset();
        vauNpFile.update(Map.of(
            new VauNpKey("SMC-B-1", "192.168.178.42", "1786_A1", "epa-as-1.dev.epa4all.de"), "34532874523875",
            new VauNpKey("SMC-B-2", "192.168.178.23", "1786_A1", "epa-as-2.dev.epa4all.de"), "03295803985486"
        ));
        Map<VauNpKey, String> map = vauNpFile.get();
        assertEquals(2, map.size());

        prepareIdpStubs();
        prepareVauStubs();
        prepareKonnektorStubs();

        given()
            .queryParams(Map.of("backends", "localhost"))
            .when()
            .get("/vau/reload")
            .then()
            .statusCode(200);

        map = vauNpFile.get();
        assertEquals(3, map.size());
        assertFalse(map.get(new VauNpKey("SMC-B-11", "localhost", "1786_A1", "localhost:9443")).isEmpty());
    }
}

