package de.servicehealth.epa4all.server.pharmacy;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.Subscription;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.serviceport.IServicePortAggregator;
import de.servicehealth.epa4all.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static de.servicehealth.epa4all.server.pharmacy.PersoenlicheVersichertendateXmlUtils.documentBuilder;
import static de.servicehealth.epa4all.server.pharmacy.PersoenlicheVersichertendateXmlUtils.getPatient;

@ApplicationScoped
public class PharmacyService {

    private static final Logger log = Logger.getLogger(PharmacyService.class.getName());

    private final MultiKonnektorService multiKonnektorService;
    private final IKonnektorClient konnektorClient;

    @Inject
    public PharmacyService(
        MultiKonnektorService multiKonnektorService,
        IKonnektorClient konnektorClient
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.konnektorClient = konnektorClient;
    }

    public String getKVNR(
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        ReadVSDResponse readVSDResponse = readVSD(correlationId, egkHandle, smcbHandle, runtimeConfig);

        byte[] pnw = readVSDResponse.getPruefungsnachweis();
        String decodedXMLFromPNW = new String(new GZIPInputStream(new ByteArrayInputStream(pnw)).readAllBytes());
        Document doc = documentBuilder.parse(new ByteArrayInputStream(decodedXMLFromPNW.getBytes()));
        String e = doc.getElementsByTagName("E").item(0).getTextContent();
        if (e.equals("3")) {
            UCPersoenlicheVersichertendatenXML patient = getPatient(readVSDResponse.getPersoenlicheVersichertendaten());
            String versichertenID = patient.getVersicherter().getVersichertenID();
            log.fine("VSDM result: " + e + " VersichertenID: " + versichertenID);
            return versichertenID;
        } else {
            String pn = doc.getElementsByTagName("PZ").item(0).getTextContent();
            String base64PN = new String(DatatypeConverter.parseBase64Binary(pn));
            return base64PN.substring(0, 10);
        }
    }

    public ReadVSDResponse readVSD(
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        IServicePortAggregator servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        if (egkHandle == null) {
            List<Card> cards = konnektorClient.getCards(runtimeConfig, CardType.EGK);
            egkHandle = cards.getFirst().getCardHandle();

        }
        if (smcbHandle == null) {
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

        ReadVSD readVSD = new ReadVSD();
        readVSD.setContext(context);
        readVSD.setEhcHandle(egkHandle);
        readVSD.setHpcHandle(smcbHandle);
        readVSD.setReadOnlineReceipt(true);
        readVSD.setPerformOnlineCheck(true);
        return servicePorts.getVSDServicePortType().readVSD(readVSD);
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
