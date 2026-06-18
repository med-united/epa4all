package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.FeatureConfig;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class KvnrEpaIT extends AbstractWiremockTest {

    @Inject
    protected FeatureConfig featureConfig;

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(featureConfig, FeatureConfig.class);
    }

    @Test
    public void insurantHasEpaButNoPoppTokenPassed() {
        enablePoppFeature(true);

        epaMultiService.doStart();
        prepareInformationStubs(NO_CONTENT.getStatusCode());
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(UNAUTHORIZED.getStatusCode(), response.getStatusCode());
    }

    @Test
    public void insurantHasEpaButPoppTokenForOtherKVRNPassed() {
        enablePoppFeature(true);

        epaMultiService.doStart();
        prepareInformationStubs(NO_CONTENT.getStatusCode());
        String poppToken = "eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzc2NTQyOTI5LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3NjU0MjkyOSwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.Uqh-NBl3O0jd-xeTR7N0ZLHGkqHo3XBOT-TVh0q8l3BrkBls6dtceZha-1RC2NOXpTkmnjAbAi8m7dlJUcGC-g";
        Response response = given().header("popp", poppToken).queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(UNAUTHORIZED.getStatusCode(), response.getStatusCode());
    }

    @SuppressWarnings("SameParameterValue")
    private void enablePoppFeature(boolean enabled) {
        FeatureConfig featureConfig = mock(FeatureConfig.class);
        when(featureConfig.isPoppEnabled()).thenReturn(enabled);
        QuarkusMock.installMockForType(featureConfig, FeatureConfig.class);
    }

    @Test
    public void insurantHasEpa() {
        epaMultiService.doStart();
        prepareInformationStubs(NO_CONTENT.getStatusCode());
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(NO_CONTENT.getStatusCode(), response.getStatusCode());
    }

    @Test
    public void insurantHasNoEpa() {
        epaMultiService.doStart();
        prepareInformationStubs(NOT_FOUND.getStatusCode());
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(NOT_FOUND.getStatusCode(), response.getStatusCode());
    }

    @Test
    public void insurantCheckEpaBadRequest() {
        epaMultiService.doStart();
        prepareInformationStubs(BAD_REQUEST.getStatusCode());
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatusCode());
    }
}
