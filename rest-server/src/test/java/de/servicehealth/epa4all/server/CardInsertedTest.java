package de.servicehealth.epa4all.server;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.domain.eventservice.event.DecodeResult;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.config.KonnektorConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.server.cetp.CETPEventHandler;
import de.servicehealth.epa4all.server.cetp.mapper.event.EventMapper;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.pharmacy.PharmacyService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
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
public class CardInsertedTest {

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    EventMapper eventMapper;

    @Inject
    MultiEpaService multiEpaService;

    @Test
    public void epaPdfDocumentIsSentToCardlink() throws Exception {
        CardlinkWebsocketClient cardlinkWebsocketClient = mock(CardlinkWebsocketClient.class);
        PharmacyService pharmacyService = mock(PharmacyService.class);
        when(pharmacyService.getKVNR(any(), any(), any(), any())).thenReturn("Z123456789");
        CETPEventHandler cetpServerHandler = new CETPEventHandler(
            cardlinkWebsocketClient, defaultUserConfig, pharmacyService, multiEpaService
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
