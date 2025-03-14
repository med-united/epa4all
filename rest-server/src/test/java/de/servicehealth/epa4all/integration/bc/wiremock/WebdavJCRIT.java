package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.jcr.RepositoryService;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("UnusedReturnValue")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavJCRIT extends AbstractWiremockTest {

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    RepositoryService repositoryService;

    @Inject
    VsdService vsdService;

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
        RestAssured.config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 600000));
        
        repositoryService.onStart();

        // TODO implement runtime JCR file processing!!!

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder";

        // String searchRequest = """
        //     <d:searchrequest xmlns:d="DAV:" xmlns:epa="https://www.service-health.de/epa">
        //         <d:JCR-SQL2><![CDATA[
        //                 SELECT r.[epa:displayname] FROM [nt:resource] as r
        //                  WHERE ISDESCENDANTNODE(r, '%s')
        //                  AND Contains([jcr:data], 'PersoenlicheVersichertendaten')
        //         ]]></d:JCR-SQL2>
        //     </d:searchrequest>
        //     """.formatted("/" + telematikId + "/jcr:root/rootFolder");

        // SELECT * FROM [nt:resource] As node WHERE node.[epa:displayname] like '%Pruefungsnachweis%'

        // xmlns:epa="https://www.service-health.de/epa"

        /*
            <d:searchrequest xmlns:d="DAV:" >
                <d:JCR-SQL2><![CDATA[
                    SELECT * FROM [nt:resource]
                ]]></d:JCR-SQL2>
            </d:searchrequest>
         */

        // SELECT * FROM [nt:resource] WHERE Contains([jcr:data], 'PersoenlicheVersichertendaten')
        
        String searchRequest = """
            <d:searchrequest xmlns:d="DAV:" >
                <d:JCR-SQL2><![CDATA[
                    SELECT * FROM [nt:resource]
                ]]></d:JCR-SQL2>
            </d:searchrequest>
            """; //.formatted("/" + telematikId + "/jcr:root/rootFolder");

        XmlPath xmlPath = searchCall(searchRequest, resource);

        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(5, hrefs.size());
    }

    private XmlPath searchCall(String prop, String resource) {
        RequestSpecification requestSpec = given().contentType("text/xml").body(prop);
        ValidatableResponse response = requestSpec
            .when()
            .request("SEARCH", resource)
            .then()
            .statusCode(207);

        XmlPath xmlPath = response.extract().body().xmlPath();
        System.out.println(xmlPath.prettify());
        return xmlPath;
    }
}