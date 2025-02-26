package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ResponseBodyExtractionOptions;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator.X_LIMIT;
import static de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator.X_OFFSET;
import static de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator.X_SORT_BY;
import static de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator.X_TOTAL_COUNT;
import static de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy.Latest;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYYMMDD;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.WebDavProp.LOCALDATE_YYYY_MM_DD;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavIT extends AbstractWiremockTest {

    @Inject
    VsdService vsdService;

    @Inject
    FolderService folderService;

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    private UCPersoenlicheVersichertendatenXML.Versicherter.Person prepareInsurantFiles(
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
        return person;
    }

    @Test
    public void filesAreReturnedWithoutParentFolder() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav/" + telematikId + "/" + kvnr + "/local";

        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <firstname/>
                    <lastname/>
                    <birthday/>
                </prop>
            </propfind>
            """;

        List<Pair<String, String>> headers = List.of(
            Pair.of(X_LIMIT, "3")
        );
        XmlPath xmlPath = propfindCall(prop, resource, 1, headers, 6);
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(3, hrefs.size());
        List<Long> timestamps = xmlPath.getList("multistatus.response.responsedescription").stream()
            .map(o -> Long.parseLong(String.valueOf(o))).toList();
        assertTrue(timestamps.getFirst() >= timestamps.get(1) && timestamps.get(1) >= timestamps.getLast());
    }

    @Test
    public void foldersAreReturnedWithoutRootAndChecksumFile() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav/" + telematikId;

        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <firstname/>
                    <lastname/>
                    <birthday/>
                </prop>
            </propfind>
            """;

        XmlPath xmlPath = propfindCall(prop, resource, 2, List.of(), 4);
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(4, hrefs.size());
    }

    private void printFilesInfo(String telematikId, String kvnr) {
        System.out.println("---------");
        List<File> leafFiles = folderService.getLeafFiles(new File(tempDir.toFile(), telematikId + "/" + kvnr));
        for (File file : leafFiles) {
            System.out.println(file.getAbsolutePath() + " -> " + file.lastModified() + "\r\n");
        }
        System.out.println("---------");
    }

    @Test
    public void medicationFoldersAreReturnedSorted() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav/" + telematikId + "/" + kvnr;

        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <firstname/>
                    <lastname/>
                    <birthday/>
                </prop>
            </propfind>
            """;

        printFilesInfo(telematikId, kvnr);

        List<Pair<String, String>> headers = List.of(
            Pair.of(X_LIMIT, "2"),
            Pair.of(X_SORT_BY, Latest.name())
        );
        XmlPath xmlPath = propfindCall(prop, resource, 1, headers, null);
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(2, hrefs.size());
        assertTrue(hrefs.getFirst().endsWith("local"));

        // ---

        headers = List.of(
            Pair.of(X_OFFSET, "2"),
            Pair.of(X_LIMIT, "2"),
            Pair.of(X_SORT_BY, Latest.name())
        );
        xmlPath = propfindCall(prop, resource, 1, headers, null);
        hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(1, hrefs.size());
        assertTrue(hrefs.getFirst().endsWith("other"));

        // ---

        TimeUnit.SECONDS.sleep(1);

        File other = new File(tempDir.toFile(), telematikId + "/" + kvnr + "/eab");
        File someFile = new File(other, "some.txt");
        boolean created = someFile.createNewFile();
        assertTrue(created);

        headers = List.of(
            Pair.of(X_LIMIT, "1"),
            Pair.of(X_SORT_BY, Latest.name())
        );

        printFilesInfo(telematikId, kvnr);

        xmlPath = propfindCall(prop, resource, 1, headers, null);
        hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertTrue(hrefs.getFirst().endsWith("eab"));
    }

    @Test
    public void foldersAreReturnedSorted() throws Exception {
        RestAssured.config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 60000));

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav";
        String prop = """
            <propfind xmlns="DAV:">
                <prop>
                    <firstname/>
                    <lastname/>
                    <birthday/>
                </prop>
            </propfind>
            """;

        List<Pair<String, String>> headers = List.of(
            Pair.of(X_LIMIT, "2"),
            Pair.of(X_SORT_BY, Latest.name())
        );
        XmlPath xmlPath = propfindCall(prop, resource, 1, headers, null);
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(1, hrefs.size());
        assertTrue(hrefs.getFirst().endsWith("Testkarte--883110000162363"));
    }

    @Test
    public void propsEntireWorkflowWorks() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = prepareInsurantFiles(telematikId, kvnr);

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
        assertEquals(10, getResponsesNodeList(responseBody).getLength());

        resource = "/webdav/";

        ValidatableResponse response = propfindCall("", resource, 100);
        ResponseBodyExtractionOptions body = response.extract().body();
        responseBody = body.xmlPath().prettify();
        System.out.println(responseBody);
        response
            .body(containsString("firstname"))
            .body(containsString("lastname"))
            .body(containsString("birthday"));

        assertEquals(11, getResponsesNodeList(responseBody).getLength());

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

        assertEquals(3, getResponsesNodeList(responseBody).getLength());

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
    }

    private NodeList getResponsesNodeList(String responseBody) throws Exception {
        Document document = InsuranceXmlUtils.createDocument(responseBody.getBytes());
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        XPathExpression xPathExpression = xPath.compile("/multistatus/response");
        return (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
    }

    private ValidatableResponse propfindCall(String prop, String resource, int depth) {
        RequestSpecification requestSpec = given()
            .header("Depth", String.valueOf(depth))
            .contentType("application/xml")
            .body(prop);
        return requestSpec
            .when()
            .request("PROPFIND", resource)
            .then()
            .statusCode(200);
    }

    private XmlPath propfindCall(
        String prop,
        String resource,
        int depth,
        List<Pair<String, String>> headers,
        Integer expectedCount
    ) {
        RequestSpecification requestSpec = given()
            .header("Depth", String.valueOf(depth))
            .contentType("application/xml")
            .body(prop);
        headers.forEach(p -> requestSpec.header(p.getKey(), p.getValue()));
        ValidatableResponse response = requestSpec
            .when()
            .request("PROPFIND", resource)
            .then()
            .statusCode(200);

        if (expectedCount != null) {
            response.header(X_TOTAL_COUNT, equalTo(String.valueOf(expectedCount)));
        }

        XmlPath xmlPath = response.extract().body().xmlPath();
        System.out.println(xmlPath.prettify());
        return xmlPath;
    }
}