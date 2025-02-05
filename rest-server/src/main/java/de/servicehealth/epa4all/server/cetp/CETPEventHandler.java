package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import de.servicehealth.feature.FeatureConfig;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CETPEventHandler.class.getName());

    private final Event<WebSocketPayload> webSocketPayloadEvent;
    private final InsuranceDataService insuranceDataService;
    private final EpaFileDownloader epaFileDownloader;
    private final IKonnektorClient konnektorClient;
    private final EpaMultiService epaMultiService;
    private final RuntimeConfig runtimeConfig;
    private final VauNpProvider vauNpProvider;
    private final FeatureConfig featureConfig;
    private final EpaCallGuard epaCallGuard;

    public CETPEventHandler(
        Event<WebSocketPayload> webSocketPayloadEvent,
        InsuranceDataService insuranceDataService,
        EpaFileDownloader epaFileDownloader,
        IKonnektorClient konnektorClient,
        EpaMultiService epaMultiService,
        CardlinkClient cardlinkClient,
        VauNpProvider vauNpProvider,
        RuntimeConfig runtimeConfig,
        FeatureConfig featureConfig,
        EpaCallGuard epaCallGuard
    ) {
        super(cardlinkClient);

        this.webSocketPayloadEvent = webSocketPayloadEvent;
        this.insuranceDataService = insuranceDataService;
        this.epaFileDownloader = epaFileDownloader;
        this.konnektorClient = konnektorClient;
        this.epaMultiService = epaMultiService;
        this.runtimeConfig = runtimeConfig;
        this.vauNpProvider = vauNpProvider;
        this.featureConfig = featureConfig;
        this.epaCallGuard = epaCallGuard;
    }

    @Override
    protected String getTopicName() {
        return "CARD/INSERTED";
    }

    private void logCardInsertedEvent(Map<String, String> paramsMap, String correlationId) {
        String paramsStr = paramsMap.entrySet().stream()
            .filter(p -> !p.getKey().equals("CardHolderName"))
            .map(p -> String.format("key=%s value=%s", p.getKey(), p.getValue())).collect(Collectors.joining(", "));

        log.debug(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap) {
        // Keep MDC names in sync with virtual-nfc-cardlink
        String correlationId = UUID.randomUUID().toString();
        MDC.put("requestCorrelationId", correlationId);
        MDC.put("iccsn", paramsMap.getOrDefault("ICCSN", "NoICCSNProvided"));
        MDC.put("ctid", paramsMap.getOrDefault("CtID", "NoCtIDProvided"));
        MDC.put("slot", paramsMap.getOrDefault("SlotID", "NoSlotIDProvided"));

        log.info("%s event received with the following payload: %s".formatted(getTopicName(), paramsMap));

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
                String egkHandle = paramsMap.get("CardHandle");
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);

                InsuranceData insuranceData = insuranceDataService.getData(telematikId, egkHandle, runtimeConfig);
                if (insuranceData == null) {
                    if (featureConfig.isExternalPnwEnabled()) {
                        log.warn(String.format(
                            "PNW is not found for EGK=%s, ReadVSD is disabled, use external PNW call", egkHandle
                        ));
                        return;
                    } else {
                        insuranceData = insuranceDataService.initData(telematikId, egkHandle, null, smcbHandle, runtimeConfig);
                    }
                }
                String insurantId = insuranceData.getInsurantId();
                EpaAPI epaApi = epaMultiService.getEpaAPI(insurantId);
                String backend = epaApi.getBackend();
                String konnektorHost = configurations.getKonnektorHost();
                String workplaceId = configurations.getWorkplaceId();
                Map<String, String> xHeaders = prepareXHeaders(epaApi, insurantId, smcbHandle, konnektorHost, workplaceId);
                try (Response response = epaCallGuard.callAndRetry(backend, () -> epaApi.getFhirProxy().forwardGet("fhir/pdf", xHeaders))) {
                    byte[] bytes = response.readEntity(byte[].class);
                    EpaContext epaContext = new EpaContext(backend, true, insuranceData, Map.of());
                    handleDownloadResponse(bytes, ctId, telematikId, epaContext, insurantId);
                    String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                    Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                    cardlinkClient.sendJson(correlationId, iccsn, "eRezeptBundlesFromAVS", payload);
                }
            } catch (Exception e) {
                log.warn(String.format("[%s] Could not get medication PDF", correlationId), e);
                String error = printException(e);
                cardlinkClient.sendJson(
                    correlationId,
                    iccsn,
                    "receiveTasklistError",
                    Map.of("slotId", slotId, "cardSessionId", "null", "status", 500, "tistatus", "500", "errormessage", error)
                );
            }
        } else {
            String msgFormat = "Ignored \"CARD/INSERTED\" values=%s";
            log.info(String.format(msgFormat, paramsMap));
        }
    }

    private Map<String, String> prepareXHeaders(
        EpaAPI epaApi,
        String insurantId,
        String smcbHandle,
        String konnectorHost,
        String workplaceId
    ) {
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();
        String epaBackend = epaApi.getBackend();
        Map<String, String> map = new HashMap<>(Map.of(
            X_INSURANT_ID, insurantId,
            X_BACKEND, epaBackend,
            X_USER_AGENT, userAgent
        ));
        vauNpProvider.getVauNp(smcbHandle, konnectorHost, workplaceId, epaBackend).ifPresent(vauNp -> map.put(VAU_NP, vauNp));
        return map;
    }

    private void handleDownloadResponse(
        byte[] bytes,
        String ctId,
        String telematikId,
        EpaContext epaContext,
        String kvnr
    ) throws Exception {
        String taskId = UUID.randomUUID().toString();
        String fileName = UUID.randomUUID() + ".pdf";
        FileDownload fileDownload = new FileDownload(epaContext, taskId, fileName, telematikId, kvnr, null);

        webSocketPayloadEvent.fireAsync(new WebSocketPayload(ctId, telematikId, kvnr, Base64.getEncoder().encodeToString(bytes)));

        RetrieveDocumentSetResponseType.DocumentResponse documentResponse = new RetrieveDocumentSetResponseType.DocumentResponse();
        documentResponse.setDocument(bytes);
        documentResponse.setMimeType("application/pdf");
        documentResponse.setDocumentUniqueId(fileName);

        epaFileDownloader.handleDownloadResponse(fileDownload, documentResponse);
    }
}
