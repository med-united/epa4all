package de.servicehealth.epa4all.server.cetp;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.ws.conn.eventservice.v7.Event;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.servicehealth.epa4all.config.api.IUserConfigurations;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.MDC;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;

public class CETPEventHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(CETPEventHandler.class.getName());

    CardlinkWebsocketClient cardlinkWebsocketClient;

    IParser parser = FhirContext.forR4().newXmlParser();

    public CETPEventHandler(
        CardlinkWebsocketClient cardlinkWebsocketClient
    ) {
        this.cardlinkWebsocketClient = cardlinkWebsocketClient;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            String correlationId = UUID.randomUUID().toString();
            MDC.put("requestCorrelationId", correlationId); // Keep MDC name in snyc with virtual-nfc-cardlink
            cardlinkWebsocketClient.connect();

            @SuppressWarnings("unchecked")
            Pair<Event, IUserConfigurations> input = (Pair<Event, IUserConfigurations>) msg;
            Event event = input.getKey();

            if (event.getTopic().equals("CARD/INSERTED")) {
                final Map<String, String> eventMap = event.getMessage().getParameter().stream()
                    .collect(Collectors.toMap(Event.Message.Parameter::getKey, Event.Message.Parameter::getValue));

                // Keep MDC names in sync with virtual-nfc-cardlink
                MDC.put("iccsn", eventMap.getOrDefault("ICCSN", "NoICCSNProvided"));
                MDC.put("ctid", eventMap.getOrDefault("CtID", "NoCtIDProvided"));
                MDC.put("slot", eventMap.getOrDefault("SlotID", "NoSlotIDProvided"));
                log.fine("CARD/INSERTED event received with the following payload: %s".formatted(eventMap));

                boolean isEGK = "EGK".equalsIgnoreCase(eventMap.get("CardType"));
                boolean hasCardHandle = eventMap.containsKey("CardHandle");
                boolean hasSlotID = eventMap.containsKey("SlotID");
                boolean hasCtID = eventMap.containsKey("CtID");
                if (isEGK && hasCardHandle && hasSlotID && hasCtID) {
                    String cardHandle = eventMap.get("CardHandle");
                    Integer slotId = Integer.parseInt(eventMap.get("SlotID"));
                    String ctId = eventMap.get("CtID");
                    String iccsn = eventMap.get("ICCSN");
                    Long endTime = System.currentTimeMillis();

                    String paramsStr = event.getMessage().getParameter().stream()
                        .filter(p -> !p.getKey().equals("CardHolderName"))
                        .map(p -> String.format("key=%s value=%s", p.getKey(), p.getValue())).collect(Collectors.joining(", "));

                    log.fine(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
                    try {
                        IUserConfigurations uc = input.getValue();

                        // TODO get FHIR Pdf

                        byte[] bytes = new byte[0];
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
                    String msgFormat = "Ignored \"CARD/INSERTED\" event=%s: values=%s";
                    log.log(Level.INFO, String.format(msgFormat, event.getMessage(), eventMap));
                }
            }

        } finally {
            cardlinkWebsocketClient.close();
            MDC.clear();
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (log.isLoggable(Level.FINE)) {
            String port = "unknown";
            if (ctx.channel().localAddress() instanceof InetSocketAddress inetSocketAddress) {
                port = String.valueOf(inetSocketAddress.getPort());
            }
            log.fine(String.format("New CETP connection established (on port %s)", port));
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (log.isLoggable(Level.FINE)) {
            String port = "unknown";
            if (ctx.channel().localAddress() instanceof InetSocketAddress inetSocketAddress) {
                port = String.valueOf(inetSocketAddress.getPort());
            }
            log.fine(String.format("CETP connection was closed (on port %s)", port));
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "Caught an exception handling CETP input", cause);
        ctx.close();
    }
}
