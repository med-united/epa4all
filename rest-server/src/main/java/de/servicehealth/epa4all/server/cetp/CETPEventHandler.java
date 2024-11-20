package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import org.jboss.logging.MDC;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = Logger.getLogger(CETPEventHandler.class.getName());

    private final InsuranceDataService insuranceDataService;
    private final IKonnektorClient konnektorClient;
    private final MultiEpaService multiEpaService;
    private final RuntimeConfig runtimeConfig;
    private final VauNpProvider vauNpProvider;

    public CETPEventHandler(
        CardlinkWebsocketClient cardlinkWebsocketClient,
        InsuranceDataService insuranceDataService,
        IKonnektorClient konnektorClient,
        MultiEpaService multiEpaService,
        VauNpProvider vauNpProvider,
        RuntimeConfig runtimeConfig
    ) {
        super(cardlinkWebsocketClient);

        this.insuranceDataService = insuranceDataService;
        this.konnektorClient = konnektorClient;
        this.multiEpaService = multiEpaService;
        this.runtimeConfig = runtimeConfig;
        this.vauNpProvider = vauNpProvider;
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
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);

                InsuranceData insuranceData = insuranceDataService.getInsuranceDataOrReadVSD(
                    telematikId, correlationId, cardHandle, runtimeConfig
                );
                String insurantId = insuranceData.getInsurantId();
                EpaAPI epaAPI = multiEpaService.getEpaAPI(insurantId);

                String vauNp = vauNpProvider.getVauNp(configurations.getConnectorBaseURL(), epaAPI.getBackend());
                byte[] bytes = epaAPI.getRenderClient().getPdfBytes(
                    Map.of(X_INSURANT_ID, insurantId, X_USER_AGENT, USER_AGENT, VAU_NP, vauNp)
                );
                String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                cardlinkWebsocketClient.sendJson(correlationId, iccsn, "eRezeptBundlesFromAVS", payload);
            } catch (Exception e) {
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
