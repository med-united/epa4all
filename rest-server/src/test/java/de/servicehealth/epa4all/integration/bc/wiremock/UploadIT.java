package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.api.epa4all.IDocumentManagementPortTypeProvider;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.getBinaryFixture;
import static de.servicehealth.epa4all.xds.classification.de.AuthorClassificationBuilder.AUTHOR_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.ClassCodeClassificationBuilder.CLASS_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.FacilityTypeCodeClassificationBuilder.FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.PracticeSettingCodeClassificationBuilder.PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.TypeCodeClassificationBuilder.TYPE_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.ss.AuthorPersonClassificationBuilder.SS_AUTHOR_CLASSIFICATION_SCHEME;
import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class UploadIT extends AbstractWiremockTest {

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
        IDocumentManagementPortTypeProvider provider = mock(IDocumentManagementPortTypeProvider.class);
        when(provider.buildIDocumentManagementPortType(any(), any())).thenReturn(documentManagement);
        QuarkusMock.installMockForType(provider, IDocumentManagementPortTypeProvider.class);

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String titleHeader = "Title-111";
        String authorInstitution = "Praxis Heider";
        String authorLanrHeader = "123456669";
        String authorFirstNameHeader = "Claudia";
        String authorLastNameHeader = "Neumann";
        String authorTitleHeader = "Prof.";
        String praxisHeader = "Praxis-111";
        String practiceSettingHeader = "practiceSetting-222";
        String informationHeader = "information";
        String information2Header = "information2";

        Pair<RegistryPackageType, ExtrinsicObjectType> pair = sendAndCaptureRequest(
            documentSetCaptor,
            0,
            kvnr,
            titleHeader,
            authorInstitution,
            authorLanrHeader,
            authorFirstNameHeader,
            authorLastNameHeader,
            authorTitleHeader,
            praxisHeader,
            practiceSettingHeader,
            informationHeader,
            information2Header,
            null,
            null,
            null
        );
        RegistryPackageType registryPackageType = pair.getKey();
        ClassificationType authorPersonClassification = getClassificationType(registryPackageType, SS_AUTHOR_CLASSIFICATION_SCHEME);
        String authorInstitutionData = authorPersonClassification.getSlot().get(1).getValueList().getValue().getFirst();
        assertTrue(authorInstitutionData.startsWith(authorInstitution));

        ExtrinsicObjectType extrinsicObjectType = pair.getValue();

        String dokumentTitle = extrinsicObjectType.getName().getLocalizedString().getFirst().getValue();
        assertEquals(titleHeader, dokumentTitle);

        ClassificationType authorClassification = getClassificationType(extrinsicObjectType, AUTHOR_CLASSIFICATION_SCHEME);
        String authorData = authorClassification.getSlot().getFirst().getValueList().getValue().getFirst();
        assertTrue(authorData.startsWith(authorLanrHeader + "^" + authorFirstNameHeader + "^" + authorLastNameHeader + "^^^" + authorTitleHeader));

        ClassificationType facilityTypeCodeClassification = getClassificationType(extrinsicObjectType, FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME);
        String facilityTypeCodeValue = facilityTypeCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals(praxisHeader, facilityTypeCodeValue);

        ClassificationType practiceSettingCodeClassification = getClassificationType(extrinsicObjectType, PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME);
        String practiceSettingCodeValue = practiceSettingCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals(practiceSettingHeader, practiceSettingCodeValue);

        ClassificationType classCodeClassification = getClassificationType(extrinsicObjectType, CLASS_CODE_CLASSIFICATION_SCHEME);
        String classCodeValue = classCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals(informationHeader, classCodeValue);

        ClassificationType typeCodeClassification = getClassificationType(extrinsicObjectType, TYPE_CODE_CLASSIFICATION_SCHEME);
        String typeCodeValue = typeCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals(information2Header, typeCodeValue);

        pair = sendAndCaptureRequest(
            documentSetCaptor,
            1,
            kvnr,
            titleHeader,
            authorInstitution,
            authorLanrHeader,
            authorFirstNameHeader,
            authorLastNameHeader,
            authorTitleHeader,
            praxisHeader,
            practiceSettingHeader,
            informationHeader,
            information2Header,
            "nodeRepresentation=AUGE; name=Augenheilkunde",
            "nodeRepresentation=ADM; name=Administratives Dokument",
            "nodeRepresentation=ADCH; name=Administrative Checklisten"
        );
        extrinsicObjectType = pair.getValue();

        practiceSettingCodeClassification = getClassificationType(extrinsicObjectType, PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME);
        practiceSettingCodeValue = practiceSettingCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Augenheilkunde", practiceSettingCodeValue);

        classCodeClassification = getClassificationType(extrinsicObjectType, CLASS_CODE_CLASSIFICATION_SCHEME);
        classCodeValue = classCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Administratives Dokument", classCodeValue);

        typeCodeClassification = getClassificationType(extrinsicObjectType, TYPE_CODE_CLASSIFICATION_SCHEME);
        typeCodeValue = typeCodeClassification.getName().getLocalizedString().getFirst().getValue();
        assertEquals("Administrative Checklisten", typeCodeValue);
    }

    private ClassificationType getClassificationType(RegistryObjectType registryObjectType, String classificationScheme) {
        List<ClassificationType> classificationTypes = registryObjectType.getClassification();
        Optional<ClassificationType> classificationTypeOpt = classificationTypes.stream()
            .filter(ct -> classificationScheme.equals(ct.getClassificationScheme()))
            .findFirst();
        assertTrue(classificationTypeOpt.isPresent());
        return classificationTypeOpt.get();
    }

    private Pair<RegistryPackageType, ExtrinsicObjectType> sendAndCaptureRequest(
        ArgumentCaptor<ProvideAndRegisterDocumentSetRequestType> documentSetCaptor,
        int num,
        String kvnr,
        String titleHeader,
        String authorInstitution,
        String authorLanrHeader,
        String authorFirstNameHeader,
        String authorLastNameHeader,
        String authorTitleHeader,
        String praxisHeader,
        String practiceSettingHeader,
        String informationHeader,
        String information2Header,
        String practiceSettingClassification,
        String classCodeClassification,
        String typeCodeClassification
    ) throws Exception {
        String fileName = "Medication-List.pdf";
        RequestSpecification requestSpecification = given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(KVNR, kvnr))
            .body(getBinaryFixture(fileName))
            .contentType(APPLICATION_PDF)
            .header(USER_AGENT, "RestAssured/1.0")
            .header("Lang-Code", "de-DE")
            .header("File-Name", fileName)
            .header("Title", titleHeader)
            .header("Author-Institution", authorInstitution)
            .header("Author-Lanr", authorLanrHeader)
            .header("Author-FirstName", authorFirstNameHeader)
            .header("Author-LastName", authorLastNameHeader)
            .header("Author-Title", authorTitleHeader)
            .header("Praxis", praxisHeader)
            .header("Fachrichtung", practiceSettingHeader)
            .header("Information", informationHeader)
            .header("Information2", information2Header);

        if (practiceSettingClassification != null) {
            requestSpecification = requestSpecification.header("PracticeSettingClassification", practiceSettingClassification);
        }
        if (classCodeClassification != null) {
            requestSpecification = requestSpecification.header("ClassCodeClassification", classCodeClassification);
        }
        if (typeCodeClassification != null) {
            requestSpecification = requestSpecification.header("TypeCodeClassification", typeCodeClassification);
        }

        ValidatableResponse response = requestSpecification
            .when()
            .post("/xds-document/upload")
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
        ProvideAndRegisterDocumentSetRequestType requestType = capturedMessages.get(num);
        Optional<? extends IdentifiableType> extrinsicObjectTypeOpt = requestType.getSubmitObjectsRequest().getRegistryObjectList().getIdentifiable().stream().filter(idt -> {
            IdentifiableType identifiableType = idt.getValue();
            return identifiableType instanceof ExtrinsicObjectType;
        }).map(JAXBElement::getValue).findFirst();

        assertTrue(extrinsicObjectTypeOpt.isPresent());

        Optional<? extends IdentifiableType> registryPackageTypeOpt = requestType.getSubmitObjectsRequest().getRegistryObjectList().getIdentifiable().stream().filter(idt -> {
            IdentifiableType identifiableType = idt.getValue();
            return identifiableType instanceof RegistryPackageType;
        }).map(JAXBElement::getValue).findFirst();

        assertTrue(registryPackageTypeOpt.isPresent());
        
        return Pair.of((RegistryPackageType) registryPackageTypeOpt.get(), (ExtrinsicObjectType) extrinsicObjectTypeOpt.get());
    }

    @AfterEach
    public void after() {
        QuarkusMock.installMockForType(portTypeProvider, IDocumentManagementPortTypeProvider.class);
    }
}