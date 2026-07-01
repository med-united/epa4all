package de.servicehealth.epa4all.server.cetp.popp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.ws.payload.WsPoppPayload;
import jakarta.enterprise.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static de.servicehealth.logging.LogContext.voidMdc;
import static de.servicehealth.logging.LogContext.voidMdcEx;
import static de.servicehealth.logging.LogField.CT_ID;
import static de.servicehealth.logging.LogField.EGK_HANDLE;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.SLOT;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.TELEMATIKID;
import static de.servicehealth.logging.LogField.WORKPLACE;
import static de.servicehealth.utils.ServerUtils.getOriginalCause;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class PoppCetpHandler extends AbstractCETPEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PoppCetpHandler.class.getName());

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final Event<WsPoppPayload> wsPoppPayloadEvent;
    private final EntitlementService entitlementService;
    private final IKonnektorClient konnektorClient;
    private final RuntimeConfig runtimeConfig;
    private final PoppConfig poppConfig;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PoppCetpHandler(
        PoppConfig poppConfig,
        Event<WsPoppPayload> wsPoppPayloadEvent,
        EntitlementService entitlementService,
        IKonnektorClient konnektorClient,
        RuntimeConfig runtimeConfig
    ) {
        this.poppConfig = poppConfig;
        this.wsPoppPayloadEvent = wsPoppPayloadEvent;
        this.entitlementService = entitlementService;
        this.konnektorClient = konnektorClient;
        this.runtimeConfig = runtimeConfig;

        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    protected String getTopicName() {
        return "CARD/INSERTED";
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap, String eventXml) {
        String correlationId = UUID.randomUUID().toString();
        logCardInsertedEvent(paramsMap, correlationId);

        boolean hasEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (hasEGK && hasCardHandle && hasCtID) {
            String ctId = paramsMap.get("CtID");
            Long slotId = Long.parseLong(paramsMap.get("SlotID"));
            String cardHandle = paramsMap.get("CardHandle");
            String konnektorHost = configurations.getKonnektorHost();
            String workplaceId = configurations.getWorkplaceId();

            try {
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);
                voidMdcEx(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, cardHandle,
                    SMCB_HANDLE, smcbHandle,
                    TELEMATIKID, telematikId,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> {
                    PoppTokenRequest request = new PoppTokenRequest("contact-connector", cardHandle);
                    HttpRequest postPopp = HttpRequest.newBuilder(URI.create(poppConfig.getPoppClientUrl()))
                        .timeout(Duration.ofSeconds(poppConfig.getPoppClientTimeoutSec()))
                        .header(CONTENT_TYPE, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                        .build();

                    httpClient.sendAsync(postPopp, HttpResponse.BodyHandlers.ofString())
                        .thenCompose(poppResp -> {
                            if (poppResp.statusCode() != 200) {
                                String error = "Popp Client response: %d, body: %s".formatted(
                                    poppResp.statusCode(), poppResp.body()
                                );
                                return CompletableFuture.failedFuture(new PoppException(error, poppResp.statusCode()));
                            }
                            String poppToken = poppResp.body();

                            String vsdRequest;
                            try {
                                vsdRequest = objectMapper.writeValueAsString(new VsdRequest(poppToken));
                            } catch (JsonProcessingException e) {
                                return CompletableFuture.failedFuture(new PoppException(e.getMessage(), 400));
                            }
                            HttpRequest postVsd = HttpRequest.newBuilder(URI.create(poppConfig.getPoppVsdmUrl()))
                                .timeout(Duration.ofSeconds(poppConfig.getPoppVsdmTimeoutSec()))
                                .header(ACCEPT, APPLICATION_XML)
                                .POST(HttpRequest.BodyPublishers.ofString(vsdRequest))
                                .build();

                            return httpClient.sendAsync(postVsd, HttpResponse.BodyHandlers.ofString())
                                .thenCompose(vsdmResp -> {
                                    if (vsdmResp.statusCode() != 200) {
                                        String error = "VSDM 2.0 response: %d, body: %s".formatted(
                                            vsdmResp.statusCode(), vsdmResp.body()
                                        );
                                        return CompletableFuture.failedFuture(new PoppException(error, vsdmResp.statusCode()));
                                    }
                                    try {
                                        entitlementService.setEntitlementV2(telematikId, poppToken);
                                    } catch (Exception e) {
                                        log.warn("Error during setEntitlementV2", e);
                                    }
                                    WsPoppPayload payload = new WsPoppPayload(
                                        ctId, telematikId, eventXml, vsdmResp.body(), poppToken
                                    );
                                    wsPoppPayloadEvent.fireAsync(payload);

                                    return CompletableFuture.completedFuture(null);
                                });
                        })
                        .whenComplete((result, ex) -> {
                            voidMdc(Map.of(
                                CT_ID, ctId,
                                SLOT, String.valueOf(slotId),
                                EGK_HANDLE, cardHandle,
                                KONNEKTOR, konnektorHost,
                                WORKPLACE, workplaceId
                            ), () -> {
                                if (ex != null) {
                                    log.error("Popp request failed", getOriginalCause(ex));
                                }
                            });
                        });
                });
            } catch (Exception e) {
                voidMdc(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, cardHandle,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> log.warn(String.format("[%s] Could not emit WS POPP event", correlationId), getOriginalCause(e)));
            }
        }
    }

}
