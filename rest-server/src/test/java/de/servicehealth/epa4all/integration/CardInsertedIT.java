package de.servicehealth.epa4all.integration;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.servicehealth.epa4all.AbstractVsdTest;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.common.TestUtils.runWithDocker;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class CardInsertedIT extends AbstractVsdTest {

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
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

	        ArgumentCaptor<String> messageTypeCaptor = ArgumentCaptor.forClass(String.class);
	        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
	        verify(cardlinkWebsocketClient, times(1)).sendJson(any(), any(), messageTypeCaptor.capture(), mapCaptor.capture());
	
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
		QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
		QuarkusMock.installMockForType(vsdService, VsdService.class);
    }
}
