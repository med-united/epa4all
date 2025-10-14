package de.servicehealth.epa4all.integration.bc.wiremock.xds;

import de.servicehealth.api.epa4all.IDocumentManagementPortTypeProvider;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.common.TestUtils.getStringFixture;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.utils.ServerUtils.makeSimplePath;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class XDSDownloadIT extends AbstractWiremockTest {

    @InjectMock
    FileEventSender fileEventSender;

    @Inject
    IDocumentManagementPortTypeProvider portTypeProvider;

    @Test
    public void xdsDocumentIsDownloaded() throws Exception {
        String kvnr = "X110683202";

        IDocumentManagementPortType documentManagement = mock(IDocumentManagementPortType.class);

        String fixture = getStringFixture("DownloadResponse.xml");
        RetrieveDocumentSetResponseType responseType = RawSoapUtils.deserializeRetrieveDocumentSetResponse(fixture);
        when(documentManagement.documentRepositoryRetrieveDocumentSet(any())).thenReturn(responseType);

        AdhocQueryResponse adhocQueryResponse = RawSoapUtils.deserializeAdhocQueryResponse(getStringFixture("AdhocQueryResponse.xml"));
        when(documentManagement.documentRegistryRegistryStoredQuery(any())).thenReturn(adhocQueryResponse);

        IDocumentManagementPortTypeProvider provider = mock(IDocumentManagementPortTypeProvider.class);
        when(provider.buildIDocumentManagementPortType(any(), any())).thenReturn(documentManagement);
        QuarkusMock.installMockForType(provider, IDocumentManagementPortTypeProvider.class);


        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        CallInfo callInfo = new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8));
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        String telematikId = initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        String uniqueId = "2.2510931143102516844806.9391247214146045914";
        ValidatableResponse response = given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParam(KVNR, kvnr)
            .when()
            .get(XDS_DOCUMENT_PATH + "/download/" + uniqueId)
            .then()
            .statusCode(200);

        String responseXml = response.extract().body().asPrettyString();
        assertTrue(responseXml.contains(uniqueId));
        assertFalse(responseXml.contains("RegistryError"));

        // uniqueId = "urn:uuid:4125e71f-1168-4287-abd3-993f041b4c60";
        // response = given()
        //     .queryParams(Map.of(X_KONNEKTOR, "localhost"))
        //     .queryParam(KVNR, kvnr)
        //     .when()
        //     .get(XDS_DOCUMENT_PATH + "/download/" + uniqueId)
        //     .then()
        //     .statusCode(409);
        //
        // responseXml = response.extract().body().asPrettyString();
        // assertTrue(responseXml.contains("RegistryError"));
        // assertTrue(responseXml.contains("Document uniqueId is not found"));

        checkPdfFiles(telematikId, kvnr);
        verify(documentManagement, times(1)).documentRepositoryRetrieveDocumentSet(any());
    }

    private void checkPdfFiles(String telematikId, String kvnr) {
        File otherFolder = new File(tempDir.toFile(), makeSimplePath("webdav", telematikId, kvnr, "other"));
        File[] pdfFiles = otherFolder.listFiles(name -> name.getName().endsWith(".pdf"));
        assertNotNull(pdfFiles);
        assertEquals(1, pdfFiles.length);
    }

    @AfterEach
    public void after() {
        QuarkusMock.installMockForType(portTypeProvider, IDocumentManagementPortTypeProvider.class);
    }
}
