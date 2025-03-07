package de.servicehealth.epa4all.integration.bc.wiremock;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasXPath;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class ExternalPnwIT extends AbstractWiremockTest {

    // @Inject
    // RepositoryService repositoryService;

    private String initStubsAndHandleCardInsertedEvent(
        String kvnr,
        String validToPayload,
        String errorHeader,
        int informationStatus
    ) throws Exception {
        String ctId = "cardTerminal-124";

        CardlinkClient cardlinkClient = mock(CardlinkClient.class);
        mockFeatureConfig(true);
        prepareIdpStubs();

        CallInfo callInfo = new CallInfo().withPayload(validToPayload).withErrorHeader(errorHeader);
        prepareVauStubs(List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        ));
        prepareKonnektorStubs();
        prepareInformationStubs(informationStatus);

        vauNpProvider.onStart();

        String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        String telematikId = konnektorClient.getTelematikId(defaultUserConfig, smcbHandle);
        String egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);

        receiveCardInsertedEvent(
            konnektorConfigs.values().iterator().next(),
            mock(EpaFileDownloader.class),
            cardlinkClient,
            egkHandle,
            ctId
        );

        verify(cardlinkClient, never()).sendJson(any(), any(), any(), any());

        return telematikId;
    }

    @Test
    public void entitlementIsCreatedForExternalPnwAndCetpEventIsNotHandled() throws Exception {
        // repositoryService.onStart();
        
        String kvnr = "X110624006";
        String street = "Achenseeweg 150";
        String startDate = "2025-01-15T22:59:59Z";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        String telematikId = initStubsAndHandleCardInsertedEvent(kvnr, validToPayload, null, 204);

        String pnw = "H4sIAAAAAAAA/w2MXQuCMBiF/4p4K/jOmTcxB9EWKDktzcibMDQ/J4qi9u/bzXngPIdDIqGdWfBO+T32QuHqlolMpGu77IfZ1etlGY8A22xWpcyXpjOLEr45rHMhYRw2WNVepySJKUbYQRg71sHCNiKgKsIpJsApiTL6ZHwP2Osn2GkTLbcVUZh4in2q3LrLTzAYrPcy1j2wcZ3yxr9NvKrbSzX5lUtAnagQ9A9GnS9OswAAAA==";

        ValidatableResponse response = given()
            .body(pnw.getBytes())
            .queryParams(Map.of(
                "startDate", startDate,
                "street", street,
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post("/vsd/pnw")
            .then()
            .statusCode(200)
            .body(hasXPath("//startDate", containsString(startDate)))
            .body(hasXPath("//endDate", containsString(validToValue)))
            .body(hasXPath("//kvnr", containsString(kvnr)))
            .body(hasXPath("//street", containsString(street)));

        XmlPath xmlPath = response.extract().xmlPath();
        System.out.println(xmlPath.prettify());

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNotNull(validTo);

        InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
        assertNotNull(insuranceData.getPz());
    }

    @Test
    public void entitlementIsNotSetAndKVNRFoldersAreNotCreatedAndCetpEventIsNotHandledDueToEPARecordIsNotFound() throws Exception {
        String kvnr = "X110624007";
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        String telematikId = initStubsAndHandleCardInsertedEvent(kvnr, validToPayload, null, 404);

        String pnw = "H4sIAAAAAAAA/w2MXQuCMBiF/4p4K/jOmTcxB9EWKDktzcibMDQ/J4qi9u/bzXngPIdDIqGdWfBO+T32QuHqlolMpGu77IfZ1etlGY8A22xWpcyXpjOLEr45rHMhYRw2WNVepySJKUbYQRg71sHCNiKgKsIpJsApiTL6ZHwP2Osn2GkTLbcVUZh4in2q3LrLTzAYrPcy1j2wcZ3yxr9NvKrbSzX5lUtAnagQ9A9GnS9OswAAAA==";

        ValidatableResponse response = given()
            .body(pnw.getBytes())
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post("/vsd/pnw")
            .then()
            .statusCode(409)
            .body(hasXPath("//text", containsString("ePA record is not found")));

        XmlPath xmlPath = response.extract().xmlPath();
        System.out.println(xmlPath.prettify());

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);

        InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
        assertNull(insuranceData);
    }

    @Test
    public void entitlementIsNotSetAndCetpEventIsNotHandledDueToEPAInternalError() throws Exception {
        String kvnr = "X110624008";
        String errorHeader = "{\"errorCode\":\"internalError\",\"errorDetail\":\"Internal error occurred during entitlement processing.\"}";
        String telematikId = initStubsAndHandleCardInsertedEvent(kvnr, null, errorHeader, 204);

        String pnw = "H4sIAAAAAAAA/w2MXQuCMBiF/4p4K/jOmTcxB9EWKDktzcibMDQ/J4qi9u/bzXngPIdDIqGdWfBO+T32QuHqlolMpGu77IfZ1etlGY8A22xWpcyXpjOLEr45rHMhYRw2WNVepySJKUbYQRg71sHCNiKgKsIpJsApiTL6ZHwP2Osn2GkTLbcVUZh4in2q3LrLTzAYrPcy1j2wcZ3yxr9NvKrbSzX5lUtAnagQ9A9GnS9OswAAAA==";

        ValidatableResponse response = given()
            .body(pnw.getBytes())
            .queryParams(Map.of(
                X_KONNEKTOR, "localhost",
                X_INSURANT_ID, kvnr
            ))
            .when()
            .post("/vsd/pnw")
            .then()
            .statusCode(409)
            .body(hasXPath("//text", containsString("Internal error occurred during entitlement processing")));

        XmlPath xmlPath = response.extract().xmlPath();
        System.out.println(xmlPath.prettify());

        Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
        assertNull(validTo);

        InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
        assertNotNull(insuranceData.getPz());
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(featureConfig, FeatureConfig.class);
    }
}