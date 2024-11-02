package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.smcb.SmcbManager;
import de.servicehealth.epa4all.server.vsds.VSDService;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.MDC;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = Logger.getLogger(CETPEventHandler.class.getName());

    private final IKonnektorClient konnektorClient;
    private final MultiEpaService multiEpaService;
    private final SmcbManager smcbManager;
    private final VSDService vsdService;
    private final AppConfig appConfig;

    public CETPEventHandler(
        CardlinkWebsocketClient cardlinkWebsocketClient,
        IKonnektorClient konnektorClient,
        MultiEpaService multiEpaService,
        SmcbManager smcbManager,
        VSDService vsdService,
        AppConfig appConfig
    ) {
        super(cardlinkWebsocketClient);

        this.konnektorClient = konnektorClient;
        this.multiEpaService = multiEpaService;
        this.smcbManager = smcbManager;
        this.vsdService = vsdService;
        this.appConfig = appConfig;
    }

    @Override
    protected String getTopicName() {
        return "CARD/INSERTED";
    }

    private void logCardInsertedEvent(Map<String, String> paramsMap, String correlationId) {
        String paramsStr = paramsMap.entrySet().stream()
            .filter(p -> !p.getKey().equals("CardHolderName"))
            .map(p -> String.format("key=%s value=%s", p.getKey(), p.getValue())).collect(Collectors.joining(", "));

        log.fine(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap) {
        // Keep MDC names in sync with virtual-nfc-cardlink
        String correlationId = UUID.randomUUID().toString();
        MDC.put("requestCorrelationId", correlationId);
        MDC.put("iccsn", paramsMap.getOrDefault("ICCSN", "NoICCSNProvided"));
        MDC.put("ctid", paramsMap.getOrDefault("CtID", "NoCtIDProvided"));
        MDC.put("slot", paramsMap.getOrDefault("SlotID", "NoSlotIDProvided"));

        log.fine("%s event received with the following payload: %s".formatted(getTopicName(), paramsMap));

        boolean isEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasSlotID = paramsMap.containsKey("SlotID");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (isEGK && hasCardHandle && hasSlotID && hasCtID) {
            logCardInsertedEvent(paramsMap, correlationId);

            String iccsn = paramsMap.get("ICCSN");
            String ctId = paramsMap.get("CtID");
            Integer slotId = Integer.parseInt(paramsMap.get("SlotID"));
            try {
                String cardHandle = paramsMap.get("CardHandle");
                String xInsurantid = vsdService.getKVNR(correlationId, cardHandle, null, appConfig);

                // TODO refactor
                multiEpaService.setXInsurantid(xInsurantid);
                EpaAPI epaAPI = multiEpaService.getEpaAPI();
                if (epaAPI == null) {
                    throw new IllegalStateException(String.format("Insurant [%s] ePA record is not found", xInsurantid));
                }

                List<Card> cards = konnektorClient.getCards(appConfig, SMC_B);
                String smcbHandle = cards.getFirst().getCardHandle();
                Pair<X509Certificate, Boolean> x509Certificate = konnektorClient.getSmcbX509Certificate(appConfig, smcbHandle);
                // x509Certificate.getKey().getNonCriticalExtensionOIDs()

                String telematikId = "3-SMC-B-Testkarte-883110000116352"; // TODO extract from x509Certificate
                smcbManager.checkOrCreateSmcbFolders(telematikId);

                byte[] bytes = epaAPI.getRenderClient().getPdfBytes(xInsurantid, USER_AGENT);
                String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                cardlinkWebsocketClient.sendJson(correlationId, iccsn, "eRezeptBundlesFromAVS", payload);
            } catch (Exception e ) {
                log.log(Level.WARNING, String.format("[%s] Could not get medication PDF", correlationId), e);
                String error = printException(e);
                cardlinkWebsocketClient.sendJson(
                    correlationId,
                    iccsn,
                    "receiveTasklistError",
                    Map.of("slotId", slotId, "cardSessionId", "null", "status", 500, "tistatus", "500", "errormessage", error)
                );
            }
        } else {
            String msgFormat = "Ignored \"CARD/INSERTED\" values=%s";
            log.log(Level.INFO, String.format(msgFormat, paramsMap));
        }
    }
}
