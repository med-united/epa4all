package de.servicehealth.epa4all.integration;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.servicehealth.epa4all.AbstractVsdTest;
import de.servicehealth.epa4all.common.ExternalTestProfile;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.Map;

import static de.servicehealth.epa4all.common.TestUtils.runWithDocker;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(ExternalTestProfile.class)
public class ExternalPnwIT extends AbstractVsdTest {

    @Test
    public void medicationPdfUploadedForExternalPnw() throws Exception {
        runWithDocker(INFORMATION_SERVICE, () -> {
            String telematikId = "5-SMC-B-Testkarte-883110000118001";
            String egkHandle = "EGK-127";
            String kvnr = "X110485291";
            String smcbHandle = "SMC-B-123";

            mockWebdavConfig();
            mockVsdService();
            mockKonnectorClient(egkHandle, telematikId, kvnr, smcbHandle);

            CardlinkWebsocketClient cardlinkWebsocketClient = mock(CardlinkWebsocketClient.class);
            receiveCardInsertedEvent(cardlinkWebsocketClient);

            verify(cardlinkWebsocketClient, never()).sendJson(any(), any(), any(), any());

            Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            assertNull(validTo);

            EntitlementService entitlementServiceMock = mock(EntitlementService.class);
            doAnswer((Answer<Void>) invocation -> {
                insuranceDataService.updateEntitlement(Instant.now(), telematikId, kvnr);
                return null;
            }).when(entitlementServiceMock).setEntitlement(any(), any(), any(), any(), any(), any(), any());
            QuarkusMock.installMockForType(entitlementServiceMock, EntitlementService.class);

            String pnw = "H4sIAAAAAAAA/w2MSwqDMBQAryIewGf8gJSYTRMhC5P4qUI3RYj9aCNWRcXT180shmGwEtaVpo+K5QWXIraR4zqube3mO8yx/V6W8QKwzc6rNc3y6R3dwrOBddYGxmGD9extgsuCeK4XIOQhhEIfRRhOhRnxMDCC1Z3UlO0pzQJR8jAtmZ8efJe0D2Wpq5om3X5QVSSr5lNujJF1oqBR4dT01e2XdVGM4ZycEOQPGFkaTrMAAAA=";

            given()
                .body(pnw.getBytes())
                .queryParams(Map.of("x-konnektor", "localhost"))
                .when()
                .post("/vsd/pnw")
                .then()
                .statusCode(201);

            validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            assertNotNull(validTo);

            InsuranceData insuranceData = insuranceDataService.getLocalInsuranceData(telematikId, kvnr);
            assertEquals("WDExMDQ4NTI5MTE3MzIxODk5OTdVWDFjxzDPSFvdIrRmmmOWFP/aP5rakVUqQj8=", insuranceData.getPz());

            receiveCardInsertedEvent(cardlinkWebsocketClient);

            verify(cardlinkWebsocketClient, times(1)).sendJson(any(), any(), eq("eRezeptBundlesFromAVS"), any());
        });
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);

        QuarkusMock.installMockForType(entitlementService, EntitlementService.class);
    }
}
