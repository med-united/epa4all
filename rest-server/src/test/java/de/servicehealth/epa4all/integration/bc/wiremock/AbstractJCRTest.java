package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.jcr.JcrService;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;

public abstract class AbstractJCRTest extends AbstractWiremockTest {

    @Inject
    JcrService jcrService;

    protected XmlPath searchCall(String resource, String query, int statusCode) {
        String request = """
            <d:searchrequest xmlns:d="DAV:" >
                <d:JCR-SQL2><![CDATA[
                    %s
                ]]></d:JCR-SQL2>
            </d:searchrequest>
            """.formatted(query);
        return call("SEARCH", resource, request, statusCode);
    }

    protected XmlPath propfindCall(String resource, String request) {
        return call("PROPFIND", resource, request, 207);
    }

    private XmlPath call(String method, String resource, String request, int statusCode) {
        RequestSpecification requestSpec = given().contentType("text/xml").body(request);
        ValidatableResponse response = requestSpec
            .when()
            .request(method, resource)
            .then()
            .statusCode(statusCode);

        XmlPath xmlPath = response.extract().body().xmlPath();
        System.out.println(xmlPath.prettify());
        return xmlPath;
    }
}