package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractVauNpTest;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpFile;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(WireMockProfile.class)
@QuarkusTestResource(value = WiremockTestResource.class, restrictToAnnotatedClass = true)
public class RequestVauNpIT extends AbstractVauNpTest {

    @Test
    void vauNpProvisioningReloaded() throws Exception {
        new VauNpFile(configFolder).store(Map.of(
            new VauNpKey("SMC-B-1", "192.168.178.42", "epa-as-1.dev.epa4all.de"), "34532874523875",
            new VauNpKey("SMC-B-2", "192.168.178.23", "epa-as-2.dev.epa4all.de"), "03295803985486"
        ));
        Map<VauNpKey, String> map = new VauNpFile(configFolder).get();
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

        map = new VauNpFile(configFolder).get();
        assertEquals(3, map.size());
        assertFalse(map.get(new VauNpKey("SMC-B-11", "localhost", "localhost:9443")).isEmpty());
    }

    // хедеры реквест и тэг

    // TODO - test the case when some user request fails and ReloadVauNpEvent is fired for the affected ePA backend

    // @Test
    // public void vauErrorHandledCorrectly() throws Exception {
    //     // new VauNpFile(configFolder).store(Map.of(
    //     //     new VauNpKey("SMC-B-11", "localhost", "localhost:8072"), "3faa0d1bb2b2e4a066be655d84cd8279b7919b767e92bbfa5550de99abd675a3"
    //     // ));
    //
    //     clientFactory.onStart();
    //     epaMultiService.onStart();
    //
    //     EpaConfig epaConfig = epaMultiService.getEpaConfig();
    //
    //     epaMultiService.getEpaBackendMap().entrySet().stream()
    //         .filter(e -> e.getKey().startsWith("localhost"))
    //         .findFirst()
    //         .ifPresent(e -> {
    //             EpaAPI epaApi = e.getValue();
    //             AuthorizationSmcBApi authorizationSmcBApi = epaApi.getAuthorizationSmcBApi();
    //             String epaUserAgent = epaConfig.getEpaUserAgent();
    //             String backend = epaApi.getBackend();
    //             GetNonce200Response nonceResponse = authorizationSmcBApi.getNonce(epaUserAgent, backend);
    //             assertNotNull(nonceResponse);
    //         });
    // }
}

