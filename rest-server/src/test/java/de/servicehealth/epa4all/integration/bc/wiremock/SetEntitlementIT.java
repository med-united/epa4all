package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.vsd.VsdConfig;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.server.rest.consent.ConsentFunction.Medication;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class SetEntitlementIT extends AbstractWiremockTest {

    public static final String ENTITLEMENTS_PATH = "/entitlements";
    
    @InjectMock
    FileEventSender fileEventSender;

    @Inject
    protected VsdConfig vsdConfig;

    @Test
    public void entitlementWasNotSetDueToInternalError() throws Exception {
        String kvnr = "X110485291";
        String errorHeader = "{\"errorCode\":\"internalError\",\"errorDetail\":\"Internal error occurred during entitlement processing.\"}";
        CallInfo callInfo = new CallInfo().withErrorHeader(errorHeader);
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post(ENTITLEMENTS_PATH)
            .then()
            .statusCode(409);

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);
    }

    @Test
    public void entitlementIsExpiredOrEmptyUnableToSet() throws Exception {
        VsdService mockVsdService = mock(VsdService.class);
        when(mockVsdService.read(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("ERROR"));
        QuarkusMock.installMockForType(mockVsdService, VsdService.class);

        String kvnr = "X110485291";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        CallInfo callInfo = new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post(ENTITLEMENTS_PATH)
            .then()
            .statusCode(204);

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);
    }

    @Test
    public void entitlementWasNotSetDueToNoConsent() throws Exception {
        String kvnr = "X110485291";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        CallInfo callInfo = new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(204, responseFuncs, Map.of(Medication, "forbidden"));

        given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post(ENTITLEMENTS_PATH)
            .then()
            .statusCode(403);

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);
    }

    @Test
    public void entitlementWasNotSetDueToNoEPA() throws Exception {
        String kvnr = "X110485291";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        CallInfo callInfo = new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(404, responseFuncs, MEDICATION_PERMIT_MAP);

        given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post(ENTITLEMENTS_PATH)
            .then()
            .statusCode(404);

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);
    }

    @Test
    public void entitlementWasSet() throws Exception {
        String kvnr = "X110485291";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        CallInfo callInfo = new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        given()
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post(ENTITLEMENTS_PATH)
            .then()
            .statusCode(200);

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertEquals(Instant.parse(validToValue + ".00Z"), validTo);
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(vsdConfig, VsdConfig.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
    }
}
