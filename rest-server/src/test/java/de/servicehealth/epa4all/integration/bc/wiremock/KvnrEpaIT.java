package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static io.restassured.RestAssured.given;
import static io.undertow.httpcore.StatusCodes.BAD_REQUEST;
import static io.undertow.httpcore.StatusCodes.NOT_FOUND;
import static io.undertow.httpcore.StatusCodes.NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class KvnrEpaIT extends AbstractWiremockTest {

    @Test
    public void insurantHasEpa() {
        epaMultiService.doStart();
        prepareInformationStubs(NO_CONTENT);
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(NO_CONTENT, response.getStatusCode());
    }

    @Test
    public void insurantHasNoEpa() {
        epaMultiService.doStart();
        prepareInformationStubs(NOT_FOUND);
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void insurantCheckEpaBadRequest() {
        epaMultiService.doStart();
        prepareInformationStubs(BAD_REQUEST);
        Response response = given().queryParam(X_INSURANT_ID, "X110624006").when().get("/epa");
        assertEquals(BAD_REQUEST, response.getStatusCode());
    }
}
