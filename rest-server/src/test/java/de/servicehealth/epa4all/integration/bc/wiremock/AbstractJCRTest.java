package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractJCRTest extends AbstractWiremockTest {

    @Inject
    VsdService vsdService;

    protected XmlPath searchCall(String resource, String request) {
        return call("SEARCH", resource, request);
    }

    protected XmlPath propfindCall(String resource, String request) {
        return call("PROPFIND", resource, request);
    }

    private XmlPath call(String method, String resource, String request) {
        RequestSpecification requestSpec = given().contentType("text/xml").body(request);
        ValidatableResponse response = requestSpec
            .when()
            .request(method, resource)
            .then()
            .statusCode(207);

        XmlPath xmlPath = response.extract().body().xmlPath();
        System.out.println(xmlPath.prettify());
        return xmlPath;
    }

    protected void prepareInsurantFiles(
        String telematikId,
        String kvnr
    ) throws Exception {
        prepareKonnektorStubs();

        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        String egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
        String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);

        String insurantId = vsdService.read(egkHandle, smcbHandle, runtimeConfig, telematikId, null);
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        assertNotNull(person);
    }
}
