package de.servicehealth.epa4all.server.vsds;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.Subscription;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import de.health.service.config.api.UserRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import static de.servicehealth.epa4all.server.vsds.PersoenlicheVersichertendateXmlUtils.documentBuilder;
import static de.servicehealth.epa4all.server.vsds.PersoenlicheVersichertendateXmlUtils.getPatient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

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

    public String getKVNR(
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        ReadVSDResponse readVSDResponse = readVSD(correlationId, egkHandle, smcbHandle, runtimeConfig);

        Document doc = createDocument(readVSDResponse);
        String e = doc.getElementsByTagName("E").item(0).getTextContent();
        if (e.equals("3")) {
            UCPersoenlicheVersichertendatenXML patient = getPatient(readVSDResponse.getPersoenlicheVersichertendaten());
            String versichertenID = patient.getVersicherter().getVersichertenID();
            log.fine("VSDM result: " + e + " VersichertenID: " + versichertenID);
            return versichertenID;
        } else {
            String kvnr = getKVNRFromDocument(doc);
			return kvnr;
        }
    }

	public static String getKVNRFromDocument(Document doc) {
		String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
		byte[] base64Binary = DatatypeConverter.parseBase64Binary(pz);
		String base64PN = new String(base64Binary);
		String kvnr = base64PN.substring(0, 10);
		return kvnr;
	}

	public static Document createDocument(ReadVSDResponse readVSDResponse) throws IOException, SAXException {
		byte[] pnw = readVSDResponse.getPruefungsnachweis();
        String decodedXMLFromPNW = new String(new GZIPInputStream(new ByteArrayInputStream(pnw)).readAllBytes());
        Document doc = documentBuilder.parse(new ByteArrayInputStream(decodedXMLFromPNW.getBytes()));
		return doc;
	}

    public ReadVSDResponse readVSD(
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        if (egkHandle == null || "".equals(egkHandle)) {
            List<Card> cards = konnektorClient.getCards(runtimeConfig, CardType.EGK);
            egkHandle = cards.getFirst().getCardHandle();

        }
        if (smcbHandle == null || "".equals(smcbHandle)) {
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
