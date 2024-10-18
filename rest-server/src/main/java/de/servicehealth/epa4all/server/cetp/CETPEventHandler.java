package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.epa4all.medication.service.DocService;
import org.jboss.logging.MDC;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = Logger.getLogger(CETPEventHandler.class.getName());

    private final DocService docService;

    public CETPEventHandler(
        CardlinkWebsocketClient cardlinkWebsocketClient,
        DocService docService
    ) {
        super(cardlinkWebsocketClient);
        this.docService = docService;
    }

    @Override
    protected String getTopicName() {
        return "CARD/INSERTED";
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap) {
        // Keep MDC names in sync with virtual-nfc-cardlink
        String correlationId = UUID.randomUUID().toString();
        MDC.put("requestCorrelationId", correlationId);

        MDC.put("iccsn", paramsMap.getOrDefault("ICCSN", "NoICCSNProvided"));
        MDC.put("ctid", paramsMap.getOrDefault("CtID", "NoCtIDProvided"));
        MDC.put("slot", paramsMap.getOrDefault("SlotID", "NoSlotIDProvided"));
        log.fine("CARD/INSERTED event received with the following payload: %s".formatted(paramsMap));

        boolean isEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasSlotID = paramsMap.containsKey("SlotID");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (isEGK && hasCardHandle && hasSlotID && hasCtID) {

            String cardHandle = paramsMap.get("CardHandle");
            
            Integer slotId = Integer.parseInt(paramsMap.get("SlotID"));
            String ctId = paramsMap.get("CtID");
            String iccsn = paramsMap.get("ICCSN");
            Long endTime = System.currentTimeMillis();

            String paramsStr = paramsMap.entrySet().stream()
                .filter(p -> !p.getKey().equals("CardHolderName"))
                .map(p -> String.format("key=%s value=%s", p.getKey(), p.getValue())).collect(Collectors.joining(", "));

            log.fine(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
            try {
                String xInsurantid = "TODO";
                byte[] bytes = docService.getPdfBytes(xInsurantid);
                String encodedPdf = Base64.getEncoder().encodeToString(bytes);

                Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", encodedPdf);
                cardlinkWebsocketClient.sendJson(correlationId, iccsn, "eRezeptBundlesFromAVS", payload);

            } catch (Exception e ) {
                log.log(Level.WARNING, String.format("[%s] Could not get prescription for Bundle", correlationId), e);

                if (e instanceof de.gematik.ws.conn.vsds.vsdservice.v5_2.FaultMessage faultMessage) {
                    String code = faultMessage.getFaultInfo().getTrace().get(0).getCode().toString();
                    cardlinkWebsocketClient.sendJson(correlationId, iccsn, "vsdmSensorData", Map.of("slotId", slotId, "ctId", ctId, "endTime", endTime, "err", code));
                }
                if (e instanceof de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage faultMessage) {
                    String code = faultMessage.getFaultInfo().getTrace().get(0).getCode().toString();
                    cardlinkWebsocketClient.sendJson(correlationId, iccsn, "vsdmSensorData", Map.of("slotId", slotId, "ctId", ctId, "endTime", endTime, "err", code));
                }

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
