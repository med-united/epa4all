package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.io.CleanupMode.ALWAYS;
import static org.junit.jupiter.api.io.CleanupMode.NEVER;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavTest extends AbstractWiremockTest {

    private final String kvnr = "X110400886";
    private final String telematikId = "1-SMC-B-Testkarte--883110000162363";

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    @Test
    public void webdavPropertiesAreReturned(@TempDir(cleanup = ALWAYS) Path tempDir) throws Exception {
        mockWebdavConfig(tempDir.toFile());

        prepareVsdStubs();
        prepareKonnektorStubs();

        String egkHandle = "EGK-41";
        String smcbHandle = "SMC-B-10";

        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        InsuranceData insuranceData = insuranceDataService.readVsd(telematikId, egkHandle, kvnr, smcbHandle, runtimeConfig);
        UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        assertNotNull(person);
        String firstname = person.getVorname();
        String lastname = person.getNachname();
        String birthdate = person.getGeburtsdatum();

        String resource = "/webdav/" + telematikId + "/" + kvnr + "/local/ReadVSDResponseSample.xml";

        String propName = """
            <propfind xmlns="DAV:">
                <propname/>
            </propfind>
            """;
        ValidatableResponse response = propfindCall(propName, resource, 0);
        String responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString("first-name"))
            .body(containsString("last-name"))
            .body(containsString("birthdate"));

        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <first-name/>
                    <last-name/>
                    <birthdate/>
                </prop>
            </propfind>
            """;
        propfindCall(prop, resource, 0)
            .body(containsString(firstname))
            .body(containsString(lastname))
            .body(containsString(birthdate));
    }

    private ValidatableResponse propfindCall(String prop, String resource, int depth) {
        return given()
            .header("Depth", String.valueOf(depth))
            .contentType("application/xml")
            .body(prop)
            .when()
            .request("PROPFIND", resource)
            .then()
            .statusCode(200);
    }
}
