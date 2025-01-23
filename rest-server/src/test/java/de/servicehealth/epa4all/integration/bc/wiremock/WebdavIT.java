package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;
import java.time.LocalDate;

import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYYMMDD;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYY_MM_DD;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.io.CleanupMode.ALWAYS;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavIT extends AbstractWiremockTest {

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    @Test
    public void webdavPropertiesAreReturned(@TempDir(cleanup = ALWAYS) Path tempDir) throws Exception {
        String kvnr = "X110400886";
        String telematikId = "1-SMC-B-Testkarte--883110000162363";

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
        String birthday = LocalDate.parse(person.getGeburtsdatum(), LOCALDATE_YYYYMMDD).format(LOCALDATE_YYYY_MM_DD);

        String resource = "/webdav/" + telematikId + "/" + kvnr + "/local/ReadVSDResponse.xml";

        String propName = """
            <propfind xmlns="DAV:">
                <propname/>
            </propfind>
            """;
        ValidatableResponse response = propfindCall(propName, resource, 0);
        String responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString("firstname"))
            .body(containsString("lastname"))
            .body(containsString("birthday"));

        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <firstname/>
                    <lastname/>
                    <birthday/>
                </prop>
            </propfind>
            """;
        response = propfindCall(prop, resource, 0);
        responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString(firstname))
            .body(containsString(lastname))
            .body(containsString(birthday));

        resource = "/webdav/" + telematikId + "/" + kvnr;
        response = propfindCall(prop, resource, 0);
        responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString(firstname))
            .body(containsString(lastname))
            .body(containsString(birthday));

        resource = "/webdav/" + telematikId;
        response = propfindCall(prop, resource, 0);
        responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        assertFalse(responseBody.contains(firstname));
        assertFalse(responseBody.contains(lastname));
        assertFalse(responseBody.contains(birthday));
        assertTrue(responseBody.contains("firstname"));
        assertTrue(responseBody.contains("lastname"));
        assertTrue(responseBody.contains("birthday"));

        resource = "/webdav/" + telematikId + "/" + kvnr;
        response = propfindCall(prop, resource, 1);
        responseBody = response.extract().body().xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString(firstname))
            .body(containsString(lastname))
            .body(containsString(birthday));

        Document document = InsuranceXmlUtils.createDocument(responseBody.getBytes());
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        XPathExpression xPathExpression = xPath.compile("/multistatus/response");
        NodeList nodes = (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
        assertEquals(4, nodes.getLength());
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
