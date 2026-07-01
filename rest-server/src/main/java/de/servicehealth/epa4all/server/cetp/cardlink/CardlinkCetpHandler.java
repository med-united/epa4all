package de.servicehealth.epa4all.server.cetp.cardlink;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.domain.cardterminal.EgkHandle;
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
import de.servicehealth.epa4all.server.ws.payload.WsCetpPayload;
import de.servicehealth.epa4all.server.ws.payload.WsTelematikPayload;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.epa4all.server.insurance.InsuranceUtils.print;
import static de.servicehealth.epa4all.xds.structure.ExtrinsicContext.defaultExtrinsicContext;
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

public class CardlinkCetpHandler extends AbstractCETPEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CardlinkCetpHandler.class.getName());

    private final Event<WsTelematikPayload> webSocketPayloadEvent;
    private final Event<WsCetpPayload> cetpPayloadEvent;
    private final InsuranceDataService insuranceDataService;
    private final EntitlementService entitlementService;
    private final EpaFileDownloader epaFileDownloader;
    private final IKonnektorClient konnektorClient;
    private final EpaMultiService epaMultiService;
    private final CardlinkClient cardlinkClient;
    private final RuntimeConfig runtimeConfig;
    private final FeatureConfig featureConfig;
    private final EpaCallGuard epaCallGuard;

    public CardlinkCetpHandler(
        Event<WsTelematikPayload> webSocketPayloadEvent,
        Event<WsCetpPayload> cetpPayloadEvent,
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
        this.webSocketPayloadEvent = webSocketPayloadEvent;
        this.cetpPayloadEvent = cetpPayloadEvent;
        this.insuranceDataService = insuranceDataService;
        this.entitlementService = entitlementService;
        this.epaFileDownloader = epaFileDownloader;
        this.konnektorClient = konnektorClient;
        this.epaMultiService = epaMultiService;
        this.cardlinkClient = cardlinkClient;
        this.runtimeConfig = runtimeConfig;
        this.featureConfig = featureConfig;
        this.epaCallGuard = epaCallGuard;
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
        if (featureConfig.isExternalPnwEnabled()) {
            log.warn("External PNW is enabled, skipping CARD/INSERTED event processing");
            return;
        }
        WsCetpPayload wsCetpPayload = new WsCetpPayload();
        wsCetpPayload.setParameters(paramsMap);

        boolean hasEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasSlotID = paramsMap.containsKey("SlotID");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (hasEGK && hasCardHandle && hasSlotID && hasCtID) {
            String egkIccsn = paramsMap.get("ICCSN");
            String ctId = paramsMap.get("CtID");
            Long slotId = Long.parseLong(paramsMap.get("SlotID"));
            String cardHandle = paramsMap.get("CardHandle");
            String konnektorHost = configurations.getKonnektorHost();
            String workplaceId = configurations.getWorkplaceId();
            try {
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                wsCetpPayload.setSmcbHandle(smcbHandle);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);
                wsCetpPayload.setTelematikId(telematikId);
                voidMdcEx(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, cardHandle,
                    SMCB_HANDLE, smcbHandle,
                    TELEMATIKID, telematikId,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> {
                    String kvnr = konnektorClient.getKvnr(runtimeConfig, cardHandle);
                    InsuranceData insuranceData = insuranceDataService.getData(telematikId, kvnr);
                    if (insuranceData == null) {
                        EgkHandle egkHandle = new EgkHandle(cardHandle, ctId, BigInteger.valueOf(slotId));
                        insuranceData = insuranceDataService.loadInsuranceData(
                            runtimeConfig, egkHandle, smcbHandle, telematikId, kvnr
                        );
                    }
                    if (insuranceData == null) {
                        throw new IllegalStateException("Unable to read InsuranceData after VSD call");
                    }
                    String insurantId = insuranceData.getInsurantId();
                    wsCetpPayload.setKvnr(insurantId);
                    wsCetpPayload.setPersoenlicheVersichertendaten(print(insuranceData.getPersoenlicheVersichertendaten(), false));

                    Instant entitlementExpiry = entitlementService.resolveEntitlement(
                        runtimeConfig, insuranceData, smcbHandle, telematikId, insurantId
                    );

                    EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
                    String backend = epaApi.getBackend();
                    Map<String, String> xHeaders = prepareXHeaders(epaApi, insurantId, konnektorHost, workplaceId);
                    try (Response response = epaCallGuard.callAndRetry(backend, () ->
                        epaApi.getRenderProxy().getEmlPdf(xHeaders)
                    )) {
                        byte[] bytes = response.readEntity(byte[].class);
                        EpaContext epaContext = new EpaContext(insurantId, backend, entitlementExpiry, insuranceData, Map.of());
                        handleDownloadResponse(bytes, ctId, telematikId, epaContext, insurantId);
                        String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                        Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                        sendJson(correlationId, egkIccsn, "eRezeptBundlesFromAVS", payload);
                    }
                });
            } catch (Exception e) {
                wsCetpPayload.setError(e.getMessage());
                voidMdc(Map.of(
                    CT_ID, ctId,
                    SLOT, String.valueOf(slotId),
                    EGK_HANDLE, cardHandle,
                    KONNEKTOR, konnektorHost,
                    WORKPLACE, workplaceId
                ), () -> {
                    log.warn(String.format("[%s] Could not get medication PDF", correlationId), getOriginalCause(e));
                    String error = printException(e);

                    sendJson(
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

        cetpPayloadEvent.fireAsync(wsCetpPayload);
    }

    private void sendJson(String correlationId, String egkIccsn, String type, Map<String, Object> payload) {
        try {
            cardlinkClient.connect();
            cardlinkClient.sendJson(correlationId, egkIccsn, type, payload);
        } finally {
            cardlinkClient.close();
        }
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

        // Default StructureDefinition will be used
        FileDownload fileDownload = new FileDownload(taskId, telematikId, kvnr, fileName, epaContext, defaultExtrinsicContext);

        webSocketPayloadEvent.fireAsync(new WsTelematikPayload(ctId, telematikId, kvnr, Base64.getEncoder().encodeToString(bytes)));

        RetrieveDocumentSetResponseType.DocumentResponse documentResponse = new RetrieveDocumentSetResponseType.DocumentResponse();
        documentResponse.setDocument(bytes);
        documentResponse.setMimeType(APPLICATION_PDF);
        documentResponse.setDocumentUniqueId(fileName);

        epaFileDownloader.handleDownloadResponse(fileDownload, documentResponse, false);
    }
}