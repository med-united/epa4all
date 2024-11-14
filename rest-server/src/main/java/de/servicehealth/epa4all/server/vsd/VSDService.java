package de.servicehealth.epa4all.server.vsd;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.Subscription;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class VSDService {

    private static final Logger log = Logger.getLogger(VSDService.class.getName());

    private final MultiKonnektorService multiKonnektorService;
    private final IKonnektorClient konnektorClient;

    @Inject
    public VSDService(
        MultiKonnektorService multiKonnektorService,
        IKonnektorClient konnektorClient
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.konnektorClient = konnektorClient;
    }

    public ReadVSDResponse readVSD(
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        if (egkHandle == null || egkHandle.isEmpty()) {
            List<Card> cards = konnektorClient.getCards(runtimeConfig, CardType.EGK);
            egkHandle = cards.getFirst().getCardHandle();
        }
        if (smcbHandle == null || smcbHandle.isEmpty()) {
            List<Card> cards = konnektorClient.getCards(runtimeConfig, CardType.SMC_B);
            smcbHandle = cards.getFirst().getCardHandle();
        }
        log.info(egkHandle + " " + smcbHandle);

        ContextType context = servicePorts.getContextType();
        if (context.getUserId() == null || context.getUserId().isEmpty()) {
            context.setUserId(UUID.randomUUID().toString());
        }
        String subsInfo = getSubscriptionsInfo(correlationId, runtimeConfig);
        log.info(String.format(
            "[%s] readVSD for cardHandle=%s, smcbHandle=%s, subscriptions: %s", correlationId, egkHandle, smcbHandle, subsInfo
        ));

        // TODO readEPrescriptionsMXBean.increaseVSDRead();

        ReadVSD readVSD = prepareReadVSDRequest(context, egkHandle, smcbHandle);
        return servicePorts.getVSDServicePortType().readVSD(readVSD);
    }

    private ReadVSD prepareReadVSDRequest(
        ContextType context,
        String egkHandle,
        String smcbHandle
    ) {
        ReadVSD readVSD = new ReadVSD();
        readVSD.setContext(context);
        readVSD.setEhcHandle(egkHandle);
        readVSD.setHpcHandle(smcbHandle);
        readVSD.setReadOnlineReceipt(true);
        readVSD.setPerformOnlineCheck(true);
        return readVSD;
    }

    private String getSubscriptionsInfo(
        String correlationId,
        UserRuntimeConfig runtimeConfig
    ) {
        try {
            List<Subscription> subscriptions = konnektorClient.getSubscriptions(runtimeConfig);
            return subscriptions.stream()
                .map(s -> String.format("[id=%s eventTo=%s topic=%s]", s.getSubscriptionId(), s.getEventTo(), s.getTopic()))
                .collect(Collectors.joining(","));
        } catch (Throwable e) {
            String msg = String.format("[%s] Could not get active getSubscriptions", correlationId);
            log.log(Level.SEVERE, msg, e);
            return "not available";
        }
    }
}
