package de.servicehealth.epa4all.integration.bc.wiremock.xds;

import de.servicehealth.api.epa4all.IDocumentManagementPortTypeProvider;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.ChecksumFile;
import de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.RemoveObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.getBinaryFixture;
import static de.servicehealth.epa4all.common.TestUtils.getStringFixture;
import static de.servicehealth.epa4all.xds.classification.de.ClassCodeClassificationBuilder.CLASS_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.ExtrinsicAuthorClassificationBuilder.EXTRINSIC_AUTHOR_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.FacilityTypeCodeClassificationBuilder.FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.PracticeSettingCodeClassificationBuilder.PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.TypeCodeClassificationBuilder.TYPE_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static jakarta.ws.rs.core.MediaType.MEDIA_TYPE_WILDCARD;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class XDSRawUploadIT extends AbstractWiremockTest {

    @Inject
    IDocumentManagementPortTypeProvider portTypeProvider;

    @Test
    public void uploadedXDSRequestContainsRightMetadata() throws Exception {
        String validToValue = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).plusDays(1).format(ISO_LOCAL_DATE_TIME);
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        prepareVauStubs(List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8)))
        ));
        prepareInformationStubs(204);

        IDocumentManagementPortType documentManagement = mock(IDocumentManagementPortType.class);
        RegistryResponseType responseType = new RegistryResponseType();
        responseType.setStatus("Success");
        
        ArgumentCaptor<ProvideAndRegisterDocumentSetRequestType> documentSetCaptor = ArgumentCaptor.forClass(ProvideAndRegisterDocumentSetRequestType.class);
        when(documentManagement.documentRepositoryProvideAndRegisterDocumentSetB(documentSetCaptor.capture())).thenReturn(responseType);

        ArgumentCaptor<RemoveObjectsRequest> removeObjectsCaptor = ArgumentCaptor.forClass(RemoveObjectsRequest.class);
        when(documentManagement.documentRegistryDeleteDocumentSet(removeObjectsCaptor.capture())).thenReturn(responseType);

        AdhocQueryResponse adhocQueryResponse = RawSoapUtils.deserializeAdhocQueryResponse(getStringFixture("AdhocQueryResponse.xml"));
        when(documentManagement.documentRegistryRegistryStoredQuery(any())).thenReturn(adhocQueryResponse);

        IDocumentManagementPortTypeProvider provider = mock(IDocumentManagementPortTypeProvider.class);
        when(provider.buildIDocumentManagementPortType(any(), any())).thenReturn(documentManagement);
        QuarkusMock.installMockForType(provider, IDocumentManagementPortTypeProvider.class);


        String telematikId = "3-SMC-B-Testkarte--883110000147807";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String fileName = "Medication-List.pdf";
        byte[] pdfBytes = getBinaryFixture(fileName);
        ValidatableResponse response = given()
            .contentType(MULTIPART_FORM_DATA)
            .multiPart("raw_soap", getStringFixture("UploadSoapRequest.xml"), "application/xop+xml")
            .multiPart("pdf_body", fileName, pdfBytes, "application/pdf")
            .header(USER_AGENT, "RestAssured/1.0")
            .header("Lang-Code", "de-DE")
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(KVNR, kvnr))
            .when()
            .post("/xds-document/upload/raw")
            .then()
            .statusCode(200);

        String taskId = response.extract().body().asPrettyString();

        String uploadResponse = "InProgress";
        while (uploadResponse.contains("InProgress")) {
            TimeUnit.MILLISECONDS.sleep(100);
            ValidatableResponse taskResponse = given()
                .header(USER_AGENT, "RestAssured/1.0")
                .when()
                .get("xds-document/task/" + taskId)
                .then()
                .statusCode(200);
            uploadResponse = taskResponse.extract().xmlPath().prettify();
        }
        assertTrue(uploadResponse.contains("Success"));

        List<ProvideAndRegisterDocumentSetRequestType> capturedMessages = documentSetCaptor.getAllValues();
        ProvideAndRegisterDocumentSetRequestType requestType = capturedMessages.getFirst();
        Optional<? extends IdentifiableType> extrinsicObjectTypeOpt = requestType.getSubmitObjectsRequest().getRegistryObjectList().getIdentifiable().stream().filter(idt -> {
            IdentifiableType identifiableType = idt.getValue();
            return identifiableType instanceof ExtrinsicObjectType;
        }).map(JAXBElement::getValue).findFirst();

        assertTrue(extrinsicObjectTypeOpt.isPresent());
        ExtrinsicObjectType extrinsicObjectType = (ExtrinsicObjectType) extrinsicObjectTypeOpt.get();

        String dokumentTitle = extrinsicObjectType.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Dokument X110624006_08d5ee7a-704a-4f2f-9c4c-4a54f045ee5c.pdf", dokumentTitle);
        //
        ClassificationType authorClassification = getClassificationType(extrinsicObjectType, EXTRINSIC_AUTHOR_CLASSIFICATION_SCHEME);
        String authorData = authorClassification.getSlot().getFirst().getValueList().getValue().getFirst();
        assertTrue(authorData.startsWith("123456667^Lukas"));
        //
        ClassificationType facilityTypeCodeClassification = getClassificationType(extrinsicObjectType, FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME);
        String facilityTypeCodeValue = facilityTypeCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Arztpraxis", facilityTypeCodeValue);
        //
        ClassificationType practiceSettingCodeClassification = getClassificationType(extrinsicObjectType, PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME);
        String practiceSettingCodeValue = practiceSettingCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Allgemeinmedizin", practiceSettingCodeValue);

        ClassificationType classCodeClassification = getClassificationType(extrinsicObjectType, CLASS_CODE_CLASSIFICATION_SCHEME);
        String classCodeValue = classCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Brief", classCodeValue);

        ClassificationType typeCodeClassification = getClassificationType(extrinsicObjectType, TYPE_CODE_CLASSIFICATION_SCHEME);
        String typeCodeValue = typeCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Arztberichte", typeCodeValue);

        ChecksumFile checksumFile = new ChecksumFile(folderService.getInsurantFolder(telematikId, kvnr));

        List<File> medFilesPdf = folderService.getMedFilesPdf(telematikId, kvnr);
        assertTrue(medFilesPdf.stream().anyMatch(f -> f.getName().endsWith(fileName)));
        String checksum = checksumFile.calculateChecksum(pdfBytes);
        assertTrue(checksumFile.getChecksums().contains(checksum));

        response = given()
            .contentType(MEDIA_TYPE_WILDCARD)
            .header(USER_AGENT, "RestAssured/1.0")
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(KVNR, kvnr))
            .body(getBinaryFixture("DeleteSoapRequest.xml"))
            .when()
            .post("/xds-document/delete/raw")
            .then()
            .statusCode(200);

        taskId = response.extract().body().asPrettyString();

        String removeResponse = "InProgress";
        while (removeResponse.contains("InProgress")) {
            TimeUnit.MILLISECONDS.sleep(100);
            ValidatableResponse taskResponse = given()
                .header(USER_AGENT, "RestAssured/1.0")
                .when()
                .get("xds-document/task/" + taskId)
                .then()
                .statusCode(200);
            removeResponse = taskResponse.extract().xmlPath().prettify();
        }
        assertTrue(removeResponse.contains("Success"));
        assertFalse(checksumFile.getChecksums().contains(checksum));
        medFilesPdf = folderService.getMedFilesPdf(telematikId, kvnr);
        assertFalse(medFilesPdf.stream().anyMatch(f -> f.getName().endsWith(fileName)));
    }

    private ClassificationType getClassificationType(ExtrinsicObjectType extrinsicObjectType, String classificationScheme) {
        List<ClassificationType> classificationTypes = extrinsicObjectType.getClassification();
        Optional<ClassificationType> classificationTypeOpt = classificationTypes.stream()
            .filter(ct -> ct.getClassificationScheme().equals(classificationScheme))
            .findFirst();
        assertTrue(classificationTypeOpt.isPresent());
        return classificationTypeOpt.get();
    }

    @AfterEach
    public void after() {
        QuarkusMock.installMockForType(portTypeProvider, IDocumentManagementPortTypeProvider.class);
    }
}