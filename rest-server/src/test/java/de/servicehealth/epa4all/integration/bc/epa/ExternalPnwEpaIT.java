package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.servicehealth.epa4all.common.profile.ExternalEpaTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.smcb.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.common.TestUtils.runWithEpaBackends;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(ExternalEpaTestProfile.class)
public class ExternalPnwEpaIT extends AbstractVsdTest {

    private final String kvnr = "X110485291";

    private String egkHandle;
    private String telematikId;

    @BeforeEach
    public void before() throws Exception {
        egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);
        String smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        telematikId = konnektorClient.getTelematikId(defaultUserConfig, smcbHandle);

        File localFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        new VsdResponseFile(localFolder).cleanUp();
    }

    @Test
    public void medicationPdfUploadedForExternalPnw() throws Exception {
        Set<String> epaBackends = epaConfig.getEpaBackends();
        runWithEpaBackends(epaBackends, () -> {
            List<String> statuses = vauNpProvider.reload(epaBackends);
            assertTrue(statuses.getFirst().contains("OK"));

            CardlinkClient cardlinkClient = mock(CardlinkClient.class);
            receiveCardInsertedEvent(
                konnektorConfigs.values().iterator().next(),
                vauNpProvider,
                cardlinkClient,
                egkHandle
            );

            verify(cardlinkClient, never()).sendJson(any(), any(), any(), any());

            String pnw = "H4sIAAAAAAAA/w2MSwqDMBQAryIewGf8gJSYTRMhC5P4qUI3RYj9aCNWRcXT180shmGwEtaVpo+K5QWXIraR4zqube3mO8yx/V6W8QKwzc6rNc3y6R3dwrOBddYGxmGD9extgsuCeK4XIOQhhEIfRRhOhRnxMDCC1Z3UlO0pzQJR8jAtmZ8efJe0D2Wpq5om3X5QVSSr5lNujJF1oqBR4dT01e2XdVGM4ZycEOQPGFkaTrMAAAA=";

            given()
                .body(pnw.getBytes())
                .queryParams(Map.of("x-konnektor", "localhost"))
                .when()
                .post("/vsd/pnw")
                .then()
                .statusCode(201);

            Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            assertNotNull(validTo);

            InsuranceData insuranceData = insuranceDataService.getLocalInsuranceData(telematikId, kvnr);
            assertEquals("WDExMDQ4NTI5MTE3MzIxODk5OTdVWDFjxzDPSFvdIrRmmmOWFP/aP5rakVUqQj8=", insuranceData.getPz());

            receiveCardInsertedEvent(
                konnektorConfigs.values().iterator().next(),
                vauNpProvider,
                cardlinkClient,
                egkHandle
            );

            verify(cardlinkClient, times(1)).sendJson(any(), any(), eq("eRezeptBundlesFromAVS"), any());
        });
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
    }
}
