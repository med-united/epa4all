package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.common.TestUtils.getStringFixture;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class GetEntitlementsIT extends AbstractWiremockTest {

    public static final String ENTITLEMENTS_PATH = "/entitlements";

    @InjectMock
    FileEventSender fileEventSender;

    @Test
    public void getEntitlementsWorks() throws Exception {
        String kvnr = "X110485291";

        String getEntitlementsJson = getStringFixture("GetEntitlements.json");
        CallInfo callInfo = new CallInfo().withJsonPayload(getEntitlementsJson.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/entitlements", callInfo)
        );
        initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        ValidatableResponse response = given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .get(ENTITLEMENTS_PATH)
            .then()
            .statusCode(200).body(containsString("Zahnarztpraxis Dr. Marie-Ella Blankenbergç"));
    }

    @Test
    public void getEntitlementByActorWorks() throws Exception {
        String kvnr = "X110485291";
        String getEntitlementJson = getStringFixture("GetEntitlement.json");
        CallInfo callInfo = new CallInfo().withJsonPayload(getEntitlementJson.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/entitlements/2-883110000092419", callInfo)
        );
        initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        ValidatableResponse response = given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            // .pathParam(ACTOR_ID, "2-883110000092419")
            .when()
            .get(ENTITLEMENTS_PATH + "/2-883110000092419")
            .then()
            .statusCode(200).body(containsString("Zahnarztpraxis Hillary Gräfin Münchhausen"));
    }
}
