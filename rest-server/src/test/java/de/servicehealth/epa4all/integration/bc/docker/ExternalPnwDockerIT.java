package de.servicehealth.epa4all.integration.bc.docker;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.servicehealth.epa4all.common.profile.ExternalDockerTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.entitlement.EntitlementFile;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.folder.WebdavConfig;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.common.TestUtils.runWithDockerContainers;
import static de.servicehealth.folder.IFolderService.LOCAL_FOLDER;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
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
@TestProfile(ExternalDockerTestProfile.class)
public class ExternalPnwDockerIT extends AbstractVsdTest {

    private final Set<String> containers = Set.of(
        INFORMATION_SERVICE,
        VAU_PROXY_SERVER,
        ENTITLEMENT_SERVICE,
        MEDICATION_RENDER_SERVICE
    );

    private final String kvnr = "X110485291";

    private String egkHandle;
    private String smcbHandle;
    private String telematikId;

    @BeforeEach
    public void before() throws Exception {
        telematikId = "5-SMC-B-Testkarte-883110000118001";
        egkHandle = "EGK-127";
        smcbHandle = "SMC-B-123";

        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        new VsdResponseFile(localFolder).cleanUp();

        new EntitlementFile(localFolder, kvnr).reset();
    }

    @Test
    public void medicationPdfUploadedForExternalPnw() throws Exception {
        runWithDockerContainers(containers, () -> {
            mockWebdavConfig(TEST_FOLDER, null, null);
            mockVsdService(kvnr);
            mockKonnectorClient(egkHandle, telematikId, kvnr, smcbHandle);

            KonnektorConfig konnektorConfig = mockKonnektorConfig();
            CardlinkClient cardlinkClient = receiveCardInsertedEvent(konnektorConfig, egkHandle, "ctId-244");

            verify(cardlinkClient, never()).sendJson(any(), any(), any(), any());

            Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            assertNull(validTo);

            EntitlementService entitlementServiceMock = mock(EntitlementService.class);
            doAnswer((Answer<Void>) invocation -> {
                insuranceDataService.setEntitlementExpiry(Instant.now(), telematikId, kvnr);
                return null;
            }).when(entitlementServiceMock).setEntitlement(any(), any(), any(), any(), any(), any());
            QuarkusMock.installMockForType(entitlementServiceMock, EntitlementService.class);

            String pnw = "H4sIAAAAAAAA/w2MSwqDMBQAryIewGf8gJSYTRMhC5P4qUI3RYj9aCNWRcXT180shmGwEtaVpo+K5QWXIraR4zqube3mO8yx/V6W8QKwzc6rNc3y6R3dwrOBddYGxmGD9extgsuCeK4XIOQhhEIfRRhOhRnxMDCC1Z3UlO0pzQJR8jAtmZ8efJe0D2Wpq5om3X5QVSSr5lNujJF1oqBR4dT01e2XdVGM4ZycEOQPGFkaTrMAAAA=";

            given()
                .body(pnw.getBytes())
                .queryParams(Map.of(X_KONNEKTOR, "localhost"))
                .when()
                .post("/vsd/pnw")
                .then()
                .statusCode(201);

            validTo = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            assertNotNull(validTo);

            InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
            assertEquals("WDExMDQ4NTI5MTE3MzIxODk5OTdVWDFjxzDPSFvdIrRmmmOWFP/aP5rakVUqQj8=", insuranceData.getPz());

            receiveCardInsertedEvent(konnektorConfig, egkHandle, "ctId-244");
            verify(cardlinkClient, times(1)).sendJson(any(), any(), eq("eRezeptBundlesFromAVS"), any());
        });
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
        QuarkusMock.installMockForType(vauNpProvider, VauNpProvider.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(entitlementService, EntitlementService.class);
        QuarkusMock.installMockForType(folderService, FolderService.class);
    }
}