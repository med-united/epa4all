package de.servicehealth.epa4all.integration;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.eventservice.event.DecodeResult;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.AbstractVsdTest;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.common.Utils;
import de.servicehealth.epa4all.server.cetp.CETPEventHandler;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.cetp.mapper.event.EventMapper;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.vsd.VSDService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class CardInsertedIT extends AbstractVsdTest {

    @Inject
    EventMapper eventMapper;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    EpaFileDownloader epaFileDownloader;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    MultiEpaService multiEpaService;

    @Inject
    VSDService vsdService;

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
    	Utils.runWithDocker("information-service", () -> {
	    	CardlinkWebsocketClient cardlinkWebsocketClient = mock(CardlinkWebsocketClient.class);

            VauNpProvider vauNpProvider = mock(VauNpProvider.class);
            when(vauNpProvider.getVauNp(any(), any())).thenReturn("vau-np");

            KonnektorClient konnektorClientMock = mock(KonnektorClient.class);
            when(konnektorClientMock.getEgkHandle(any(), any())).thenReturn("EGK-127");
            when(konnektorClientMock.getTelematikId(any(), any())).thenReturn("5-SMC-B-Testkarte-883110000118001");
            when(konnektorClientMock.getKvnr(any(), any())).thenReturn("X110485291");
            QuarkusMock.installMockForType(konnektorClientMock, IKonnektorClient.class);

            VSDService vsdServiceMock = mock(VSDService.class);
            when(vsdServiceMock.readVSD(any(), any(), any())).thenReturn(prepareReadVSDResponse());

            QuarkusMock.installMockForType(vsdServiceMock, VSDService.class);

	        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
	        CETPEventHandler cetpServerHandler = new CETPEventHandler(
	            cardlinkWebsocketClient, insuranceDataService, epaFileDownloader, konnektorClient,
                multiEpaService, vauNpProvider, runtimeConfig
	        );
	        EmbeddedChannel channel = new EmbeddedChannel(cetpServerHandler);
	
	        String slotIdValue = "3";
	        String ctIdValue = "CtIDValue";
	
	        KonnektorConfig konnektorConfig = mock(KonnektorConfig.class);
	        IUserConfigurations configurations = mock(IUserConfigurations.class);
	        when(konnektorConfig.getUserConfigurations()).thenReturn(configurations);
	        
	        channel.writeOneInbound(decode(konnektorConfig, slotIdValue, ctIdValue));
	        channel.pipeline().fireChannelReadComplete();
	
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
    public void afterAll() {
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VSDService.class);
    }

    private DecodeResult decode(
        KonnektorConfig konnektorConfig,
        String slotIdValue,
        String ctIdValue
    ) {
        Event event = new Event();
        event.setTopic("CARD/INSERTED");
        Event.Message message = new Event.Message();
        Event.Message.Parameter parameter = new Event.Message.Parameter();
        parameter.setKey("CardHandle");
        parameter.setValue("CardHandleValue");
        Event.Message.Parameter parameterSlotId = new Event.Message.Parameter();
        parameterSlotId.setKey("SlotID");
        parameterSlotId.setValue(slotIdValue);
        Event.Message.Parameter parameterCtId = new Event.Message.Parameter();
        parameterCtId.setKey("CtID");
        parameterCtId.setValue(ctIdValue);
        Event.Message.Parameter parameterCardType = new Event.Message.Parameter();
        parameterCardType.setKey("CardType");
        parameterCardType.setValue("EGK");

        message.getParameter().add(parameter);
        message.getParameter().add(parameterSlotId);
        message.getParameter().add(parameterCtId);
        message.getParameter().add(parameterCardType);
        event.setMessage(message);
        return new DecodeResult(eventMapper.toDomain(event), konnektorConfig.getUserConfigurations());
    }
}
