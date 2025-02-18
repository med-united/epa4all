package de.servicehealth.epa4all.integration.bc.docker;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.servicehealth.epa4all.common.profile.ProxyLocalTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.common.TestUtils.runWithDockerContainers;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(ProxyLocalTestProfile.class)
public class CardInsertedDockerIT extends AbstractVsdTest {

    private final Set<String> containers = Set.of(
        INFORMATION_SERVICE,
        VAU_PROXY_SERVER,
        ENTITLEMENT_SERVICE,
        MEDICATION_RENDER_SERVICE
    );

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
        runWithDockerContainers(containers, () -> {
            String telematikId = "5-SMC-B-Testkarte-883110000118001";
            String egkHandle = "EGK-127";
            String kvnr = "X110485291";
            String smcbHandle = "SMC-B-123";

            mockWebdavConfig(TEST_FOLDER);
            mockVsdService(kvnr);
            mockKonnectorClient(egkHandle, telematikId, kvnr, smcbHandle);

            CardlinkClient cardlinkClient = mock(CardlinkClient.class);
            receiveCardInsertedEvent(
                mockKonnektorConfig(),
                cardlinkClient,
                egkHandle,
                "ctId-222"
            );

            ArgumentCaptor<String> messageTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
            verify(cardlinkClient, times(1)).sendJson(any(), any(), messageTypeCaptor.capture(), mapCaptor.capture());

            List<String> capturedMessages = messageTypeCaptor.getAllValues();
            assertTrue(capturedMessages.getFirst().contains("eRezeptBundlesFromAVS"));

            List<Map<String, Object>> maps = mapCaptor.getAllValues();
            Map<String, Object> payloadData = maps.getFirst();

            String bas64EncodedPdfContent = (String) payloadData.get("bundles");
            assertFalse(bas64EncodedPdfContent.isEmpty());
            assertTrue(bas64EncodedPdfContent.startsWith("PDF:"));
        });
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
        QuarkusMock.installMockForType(vauNpProvider, VauNpProvider.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(entitlementService, EntitlementService.class);
    }
}
