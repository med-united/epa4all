package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.ws.CETPPayload;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.epa4all.server.insurance.InsuranceUtils.print;
import static de.servicehealth.logging.LogContext.voidMdc;
import static de.servicehealth.logging.LogContext.voidMdcEx;
import static de.servicehealth.logging.LogField.CT_ID;
import static de.servicehealth.logging.LogField.EGK_HANDLE;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.SLOT;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.TELEMATIKID;
import static de.servicehealth.logging.LogField.WORKPLACE;
import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static de.servicehealth.utils.ServerUtils.getOriginalCause;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CETPEventHandler.class.getName());

    private final Event<WebSocketPayload> webSocketPayloadEvent;
    private final Event<CETPPayload> cetpPayloadEvent;
    private final InsuranceDataService insuranceDataService;
    private final EntitlementService entitlementService;
    private final EpaFileDownloader epaFileDownloader;
    private final IKonnektorClient konnektorClient;
    private final EpaMultiService epaMultiService;
    private final RuntimeConfig runtimeConfig;
    private final FeatureConfig featureConfig;
    private final EpaCallGuard epaCallGuard;

    public CETPEventHandler(
        Event<WebSocketPayload> webSocketPayloadEvent,
        Event<CETPPayload> cetpPayloadEvent,
        InsuranceDataService insuranceDataService,
        EntitlementService entitlementService,
        EpaFileDownloader epaFileDownloader,
        IKonnektorClient konnektorClient,
        EpaMultiService epaMultiService,
        CardlinkClient cardlinkClient,
        RuntimeConfig runtimeConfig,
        FeatureConfig featureConfig,
        EpaCallGuard epaCallGuard
    ) {
        super(cardlinkClient);

        this.webSocketPayloadEvent = webSocketPayloadEvent;
        this.cetpPayloadEvent = cetpPayloadEvent;
        this.insuranceDataService = insuranceDataService;
        this.entitlementService = entitlementService;
        this.epaFileDownloader = epaFileDownloader;
        this.konnektorClient = konnektorClient;
        this.epaMultiService = epaMultiService;
        this.runtimeConfig = runtimeConfig;
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

        log.info(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap) {
        String correlationId = UUID.randomUUID().toString();
        logCardInsertedEvent(paramsMap, correlationId);
        if (featureConfig.isExternalPnwEnabled()) {
            log.warn("External PNW is enabled, skipping CARD/INSERTED event processing");
            return;
        }
        CETPPayload cetpPayload = new CETPPayload();
        cetpPayload.setParameters(paramsMap);

        boolean hasEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasSlotID = paramsMap.containsKey("SlotID");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (hasEGK && hasCardHandle && hasSlotID && hasCtID) {
            String egkIccsn = paramsMap.get("ICCSN");
            String ctId = paramsMap.get("CtID");
            Integer slotId = Integer.parseInt(paramsMap.get("SlotID"));
            String egkHandle = paramsMap.get("CardHandle");
            String konnektorHost = configurations.getKonnektorHost();
            String workplaceId = configurations.getWorkplaceId();
            try {
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                cetpPayload.setSmcbHandle(smcbHandle);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);
                cetpPayload.setTelematikId(telematikId);
                voidMdcEx(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, egkHandle,
                    SMCB_HANDLE, smcbHandle,
                    TELEMATIKID, telematikId,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> {
                    String kvnr = konnektorClient.getKvnr(runtimeConfig, egkHandle);
                    InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
                    if (insuranceData == null) {
                        insuranceData = insuranceDataService.loadInsuranceData(
                            runtimeConfig, egkHandle, smcbHandle, telematikId, kvnr
                        );
                    }
                    if (insuranceData == null) {
                        throw new IllegalStateException("Unable to read InsuranceData after VSD call");
                    }
                    String insurantId = insuranceData.getInsurantId();
                    cetpPayload.setKvnr(insurantId);
                    cetpPayload.setPersoenlicheVersichertendaten(print(insuranceData.getPersoenlicheVersichertendaten(), false));

                    Instant entitlementExpiry = entitlementService.resolveEntitlement(
                        runtimeConfig, insuranceData, smcbHandle, telematikId, insurantId
                    );

                    EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
                    String backend = epaApi.getBackend();
                    Map<String, String> xHeaders = prepareXHeaders(epaApi, insurantId, konnektorHost, workplaceId);
                    try (Response response = epaCallGuard.callAndRetry(backend, () ->
                        epaApi.getFhirProxy().forwardGet("fhir/pdf", xHeaders)
                    )) {
                        byte[] bytes = response.readEntity(byte[].class);
                        EpaContext epaContext = new EpaContext(insurantId, backend, entitlementExpiry, insuranceData, Map.of());
                        handleDownloadResponse(bytes, ctId, telematikId, epaContext, insurantId);
                        String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                        Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                        cardlinkClient.sendJson(correlationId, egkIccsn, "eRezeptBundlesFromAVS", payload);
                    }
                });
            } catch (Exception e) {
                cetpPayload.setError(e.getMessage());
                voidMdc(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, egkHandle,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> {
                    log.warn(String.format("[%s] Could not get medication PDF", correlationId), getOriginalCause(e));
                    String error = printException(e);
                    cardlinkClient.sendJson(
                        correlationId,
                        egkIccsn,
                        "receiveTasklistError",
                        Map.of("slotId", slotId, "cardSessionId", "null", "status", 500, "tistatus", "500", "errormessage", error)
                    );
                });
            }
        } else {
            String msgFormat = "Ignored \"CARD/INSERTED\" values=%s";
            log.info(String.format(msgFormat, paramsMap));
        }

        cetpPayloadEvent.fireAsync(cetpPayload);
    }

    private Map<String, String> prepareXHeaders(EpaAPI epaApi, String insurantId, String konnektorHost, String workplaceId) {
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();
        String epaBackend = epaApi.getBackend();
        return new HashMap<>(Map.of(
            X_KONNEKTOR, konnektorHost,
            X_WORKPLACE, workplaceId,
            X_INSURANT_ID, insurantId,
            X_BACKEND, epaBackend,
            X_USER_AGENT, userAgent
        ));
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
        documentResponse.setMimeType(APPLICATION_PDF);
        documentResponse.setDocumentUniqueId(fileName);

        epaFileDownloader.handleDownloadResponse(fileDownload, documentResponse);
    }
}