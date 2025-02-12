package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.common.TmpFolderAction;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ResponseBodyExtractionOptions;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static de.servicehealth.epa4all.common.TestUtils.deleteFiles;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYYMMDD;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYY_MM_DD;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavIT extends AbstractWiremockTest {

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    VsdService vsdService;

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    private void inTempFolder(TmpFolderAction action) throws Exception {
        Path tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
        try {
            mockWebdavConfig(tempDir.toFile());
            action.execute(tempDir);
        } finally {
            deleteFiles(tempDir.toFile().listFiles());
            deleteFiles(new File[]{tempDir.toFile()});
        }
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
    }

    @Test
    public void propsEntireWorkflowWorks() throws Exception {
        inTempFolder(tempDir -> {
            String telematikId = "1-SMC-B-Testkarte--883110000162363";
            String kvnr = "X110400886";
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = preparePerson(telematikId);

            String firstname = person.getVorname();
            String lastname = person.getNachname();
            String birthday = LocalDate.parse(person.getGeburtsdatum(), LOCALDATE_YYYYMMDD).format(LOCALDATE_YYYY_MM_DD);

            String resource = "/webdav/";

            String propName = """
            <propfind xmlns="DAV:">
                <propname/>
            </propfind>
            """;
            final ValidatableResponse response0 = propfindCall(propName, resource, 100);
            String responseBody = response0.extract().body().xmlPath().prettify();
            System.out.println(responseBody);

            // entries,validto are skipped because ChecksumFile & EntitlementFile are not created automatically
            Arrays.asList("creationdate,getlastmodified,displayname,resourcetype,firstname,lastname,birthday,entryuuid,getcontenttype,getcontentlength".split(",")).forEach(property ->
                response0.body(containsString(property))
            );
            assertFalse(responseBody.contains(firstname));
            assertFalse(responseBody.contains(lastname));
            assertFalse(responseBody.contains(birthday));
            assertEquals(11, getResponsesNodeList(responseBody).getLength());

            resource = "/webdav/";

            ValidatableResponse response = propfindCall("", resource, 100);
            ResponseBodyExtractionOptions body = response.extract().body();
            responseBody = body.xmlPath().prettify();
            System.out.println(responseBody);
            response
                .body(containsString("firstname"))
                .body(containsString("lastname"))
                .body(containsString("birthday"));

            assertEquals(13, getResponsesNodeList(responseBody).getLength());

            resource = "/webdav";

            response = propfindCall("", resource, 1);
            responseBody = response.extract().body().xmlPath().prettify();
            System.out.println(responseBody);
            assertFalse(responseBody.contains("firstname"));
            assertFalse(responseBody.contains("lastname"));
            assertFalse(responseBody.contains("birthday"));

            resource = "/webdav/" + telematikId + "/" + kvnr + "/local/ReadVSDResponse.xml";

            propName = """
                <propfind xmlns="DAV:">
                    <propname/>
                </propfind>
                """;
            response = propfindCall(propName, resource, 0);
            responseBody = response.extract().body().xmlPath().prettify();
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

            assertEquals(5, getResponsesNodeList(responseBody).getLength());

            prop = """
                <propfind xmlns="DAV:">
                    <prop>
                        <firstname/>
                        <lastname/>
                        <birthday/>
                        <displayname/>
                        <creationdate/>
                        <someproperty/>
                    </prop>
                </propfind>
                """;

            resource = "/webdav";
            response = propfindCall(prop, resource, 10000);
            responseBody = response.extract().body().xmlPath().prettify();
            System.out.println(responseBody);
        });
    }

    private NodeList getResponsesNodeList(String responseBody) throws Exception {
        Document document = InsuranceXmlUtils.createDocument(responseBody.getBytes());
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        XPathExpression xPathExpression = xPath.compile("/multistatus/response");
        return (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
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

    private UCPersoenlicheVersichertendatenXML.Versicherter.Person preparePerson(String telematikId) throws Exception {
        prepareVsdStubs();
        prepareKonnektorStubs();

        String egkHandle = "EGK-41";
        String smcbHandle = "SMC-B-10";

        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        String insurantId = vsdService.readVsd(telematikId, egkHandle, runtimeConfig, smcbHandle, null);
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        assertNotNull(person);
        return person;
    }
}
